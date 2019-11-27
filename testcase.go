package main

import (
	"context"
	"encoding/json"
	"io"
	"io/ioutil"
	"log"
	"strings"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/push"

	"github.com/docker/docker/api/types/network"

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
)

type testCase struct {
	preRunImage, testImage string
	pushGateway            string
	waitInterval           int
	dockerCli              *client.Client
	preRunContainerID      string
	ctx                    context.Context
	cancel                 context.CancelFunc
	monitorExitC           chan struct{}
}

func newTestCase(ctx context.Context, preRunImage, testImage, pushGateway string, waitInterval int) (*testCase, error) {
	var err error
	t := &testCase{preRunImage: preRunImage, testImage: testImage, waitInterval: waitInterval}
	t.pushGateway = pushGateway
	t.dockerCli, err = client.NewClientWithOpts(client.FromEnv)
	if err != nil {
		return nil, err
	}
	cctx, cancel := context.WithCancel(ctx)
	t.ctx = cctx
	t.cancel = cancel
	return t, nil
}

func (tc *testCase) start() error {
	// start the pre-run container
	// check if the image exists
	images, err := tc.dockerCli.ImageList(tc.ctx, types.ImageListOptions{})
	if err != nil {
		return err
	}
	var shouldPullPreRunImage, shouldDelTestImage bool
	shouldPullPreRunImage = true
	for _, image := range images {
		repotag := image.RepoTags[0]
		if imageMatch(repotag, tc.preRunImage) {
			shouldPullPreRunImage = false
		} else if imageMatch(repotag, tc.testImage) {
			shouldDelTestImage = true
		}
	}

	if shouldPullPreRunImage {
		log.Printf("Pulling pre run image: %v\n", tc.preRunImage)
		events, err := tc.dockerCli.ImagePull(tc.ctx, tc.preRunImage, types.ImagePullOptions{})
		if err != nil {
			return err
		}
		defer events.Close()
		_, err = io.Copy(ioutil.Discard, events)
		if err != nil {
			return err
		}
		log.Println("Pulling finish")
	}
	if shouldDelTestImage {
		log.Println("test image exits, deleting")
		_, err = tc.dockerCli.ImageRemove(tc.ctx, tc.testImage, types.ImageRemoveOptions{})
		if err != nil {
			return err
		}
		log.Println("test image deleted")
	}
	// start and monitor pre-run image
	if err = tc.preRun(); err != nil {
		return err
	}
	time.Sleep(time.Duration(tc.waitInterval) * time.Second)
	// now should pull the test image, but don't wait it
	log.Println("Pulling test image")
	_, err = tc.dockerCli.ImagePull(tc.ctx, tc.testImage, types.ImagePullOptions{})
	if err != nil {
		return err
	}
	return nil
}

func (tc *testCase) shutdown() {
	log.Println("shutting down")
	var shouldDelTestImage bool
	tc.cancel()
	ctx := context.Background()
	images, err := tc.dockerCli.ImageList(ctx, types.ImageListOptions{})
	if err != nil {
		log.Println("cant get images in shutdown")
	}
	for _, image := range images {
		if imageMatch(image.RepoTags[0], tc.testImage) {
			shouldDelTestImage = true
			break
		}
	}
	if shouldDelTestImage {
		log.Println("removing test image")
		_, err = tc.dockerCli.ImageRemove(ctx, tc.testImage, types.ImageRemoveOptions{})
		if err != nil {
			log.Println("cant remote test image")
		}
	}
	if tc.preRunContainerID != "" {
		log.Println("stop pre run container")
		err = tc.dockerCli.ContainerRemove(ctx, tc.preRunContainerID, types.ContainerRemoveOptions{Force: true})
		if err != nil {
			log.Println(err)
		}
	}
	if tc.monitorExitC != nil {
		log.Println("wait for monitor to exit")
		<-tc.monitorExitC
	}
}

func (tc *testCase) preRun() error {
	res, err := tc.dockerCli.ContainerCreate(tc.ctx, &container.Config{Image: tc.preRunImage}, &container.HostConfig{}, &network.NetworkingConfig{}, "pre-run")
	if err != nil {
		return err
	}
	tc.preRunContainerID = res.ID
	err = tc.dockerCli.ContainerStart(tc.ctx, res.ID, types.ContainerStartOptions{})
	if err != nil {
		return err
	}
	log.Printf("Successfully start pre run container: %v\n", tc.preRunContainerID)
	tc.monitorExitC = tc.monitor()
	return nil
}

func imageMatch(repotag, imageName string) bool {
	if repotag == imageName || strings.HasPrefix(repotag, imageName) {
		return true
	}
	return false
}

func (tc *testCase) monitor() chan struct{} {
	exitC := make(chan struct{}, 1)
	go func() {
		defer close(exitC)
		testCaseDiskIO := prometheus.NewGaugeVec(prometheus.GaugeOpts{
			Name: "testcase_disk_io",
			Help: "",
		}, []string{"op"})
		registry := prometheus.NewRegistry()
		registry.MustRegister(testCaseDiskIO)
		pusher := push.New(tc.pushGateway, "testcase-"+tc.testImage).Gatherer(registry)

		var s types.StatsJSON
		var blkRead, blkWrite uint64
		response, err := tc.dockerCli.ContainerStats(tc.ctx, tc.preRunContainerID, true)
		if err != nil {
			log.Println(err)
			return
		}
		defer response.Body.Close()
		dec := json.NewDecoder(response.Body)
		ticker := time.NewTicker(time.Second * 1)
		log.Println("Start monitoring")
		for {
			select {
			case <-tc.ctx.Done():
				log.Println("monitor exits")
				goto EXIT
			case <-ticker.C:
			}
			if err := dec.Decode(&s); err != nil {
				if err != io.EOF {
					log.Println(err)
				}
				break
			}
			var currentBlkRead, currentBlkWrite uint64
			for _, bioEntry := range s.BlkioStats.IoServiceBytesRecursive {
				if len(bioEntry.Op) == 0 {
					continue
				}
				switch bioEntry.Op[0] {
				case 'r', 'R':
					currentBlkRead += bioEntry.Value
				case 'w', 'W':
					currentBlkWrite += bioEntry.Value
				}
			}
			readSpeed := float64(currentBlkRead-blkRead) / (1024 * 1024)
			writeSpeed := float64(currentBlkWrite-blkWrite) / (1024 * 1024)
			testCaseDiskIO.WithLabelValues("read").Set(readSpeed)
			testCaseDiskIO.WithLabelValues("write").Set(writeSpeed)
			blkRead = currentBlkRead
			blkWrite = currentBlkWrite
			if err = pusher.Push(); err != nil && !strings.Contains(err.Error(), "20") {
				log.Println(err)
			}
		}
	EXIT:
		testCaseDiskIO.WithLabelValues("read").Set(0)
		testCaseDiskIO.WithLabelValues("write").Set(0)
		pusher.Push()
	}()
	return exitC
}

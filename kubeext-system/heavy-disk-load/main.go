package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"strconv"
)

func main() {
	var preRunImage, testImage, gateway, waitInterval string
	preRunImage = os.Getenv("PRE_RUN_IMAGE")
	testImage = os.Getenv("TEST_IMAGE")
	gateway = os.Getenv("GATEWAY")
	waitInterval = os.Getenv("WAIT_INTERVAL")
	interval, err := strconv.Atoi(waitInterval)
	if err != nil {
		log.Panic(err)
	}
	tc, err := newTestCase(context.Background(), preRunImage, testImage, gateway, interval)
	if err != nil {
		log.Panic(err)
	}
	signals := make(chan os.Signal, 2014)
	tcErrorC := make(chan error, 1)
	signal.Notify(signals, handledSignals...)
	done := handleSignals(signals, tcErrorC, tc)
	err = tc.start()
	if err != nil {
		tcErrorC <- err
		log.Println(err)
	} else {
		log.Println("successfully start")
	}
	<-done
}

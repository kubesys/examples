package main

import (
	"log"
	"os"
	"syscall"
)

var handledSignals = []os.Signal{
	syscall.SIGTERM,
	syscall.SIGINT,
}

func handleSignals(signals chan os.Signal, tcError chan error, tc *testCase) chan struct{} {
	done := make(chan struct{}, 1)
	go func() {
		select {
		case <-signals:
			log.Println("get signals")
		case <-tcError:
			log.Println("get tc error")
		}
		tc.shutdown()
		close(done)
	}()
	return done
}

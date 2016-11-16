package main

import (
	"log"
	"os"
	"syscall"
	"unsafe"
)

func EnforceSingleInstance() {
	procCreateMutex := syscall.NewLazyDLL("kernel32.dll").NewProc("CreateMutexW")
	_, _, err := procCreateMutex.Call(
		0,
		0,
		uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr("AirComputing.AeroFS"))))

	// Exit if we failed to create the mutex because one already existed. On success or failure
	// for any other reason keep running. 183 is the windows error code for ERROR_ALREADY_EXISTS
	if err != nil && int(err.(syscall.Errno)) == 183 {
		log.Printf("An instance of updater is already running - exiting")
		os.Exit(0)
	}
}

// +build windows

package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"syscall"
)

var SETTINGS map[Product]settings = map[Product]settings{
	Client: {
		approot:  "AeroFSExec",
		rtroot:   "AeroFS",
		launcher: "aerofs.exe",
		manifest: "client-win.json",
	},
	TeamServer: {
		approot:  "AeroFSTeamServerExec",
		rtroot:   "AeroFSTeamServer",
		launcher: "aerofsts.exe",
		manifest: "team_server-win.json",
	},
}

type ProgressProcess struct {
}

func Launch(launcher string, args []string) error {
	if err := PreLaunch(launcher, args); err != nil {
		return err
	}

	if err := os.Chdir(filepath.Dir(launcher)); err != nil {
		return err
	}

	// syscall.Exec is not supported on Windows
	// exec.Command makes it super hard to start a detached process
	// go low-level with a win32 CreateProcess call
	var sI syscall.StartupInfo
	var pI syscall.ProcessInformation
	if err := syscall.CreateProcess(
		nil,
		syscall.StringToUTF16Ptr(launcher),
		nil,
		nil,
		false,
		syscall.CREATE_NEW_PROCESS_GROUP|0x00000008, // DETACHED_PROCESS
		nil,
		nil,
		&sI,
		&pI,
	); err != nil {
		return fmt.Errorf("Could not run launcher: %s %s %v %v", err.Error(), launcher, args, os.Environ())
	}
	log.Println("Sucessfully started")
	// NB: Launch is expected not to return on success
	os.Exit(0)
	return nil
}

// NB: extra command line args are ignored for now
func LaunchAero(exec string, _ []string) error {
	APPDATA := os.Getenv("APPDATA")
	if len(APPDATA) == 0 {
		PROFILE := os.Getenv("USERPROFILE")
		if len(PROFILE) == 0 {
			return fmt.Errorf("USERPROFILE not set")
		}
		APPDATA = filepath.Join(PROFILE, "AppData", "Roaming")
	}
	base := filepath.Base(exec)
	path := filepath.Dir(exec)

	var product Product = Client
	if strings.Contains(base, "ts") {
		product = TeamServer
	}

	settings := SETTINGS[product]

	LOCALAPPDATA := os.Getenv("LOCALAPPDATA")
	if len(LOCALAPPDATA) == 0 {
		return fmt.Errorf("LOCALAPPDATA not set")
	}
	rtroot := filepath.Join(LOCALAPPDATA, settings.rtroot)
	if err := setLogFile(rtroot); err != nil {
		return fmt.Errorf("Failed to set logfile:\n%s", err.Error())
	}

	approot := filepath.Join(APPDATA, settings.approot)
	launcher := filepath.Join(approot, "current", settings.launcher)
	args := []string{}

	LaunchIfMatching(approot, launcher, args)

	inst, err := Update(filepath.Join(path, "site-config.properties"),
		settings.manifest,
		approot,
		exec,
	)
	if err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		if len(inst) > 0 {
			LaunchIfMatching(approot, launcher, args)
		}
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}

	return Launch(filepath.Join(inst, settings.launcher), args)
}

func (prog *ProgressProcess) Kill() {
	log.Printf("Failed to kill progress monitor: Not implemented")
}

func (prog *ProgressProcess) Launch() {
	log.Printf("Failed to launch progress monitor: Not implemented")
}

func (prog *ProgressProcess) IncrementProgress(progress int) {
	return
}

func NewProgressMonitor(total int, exec string) *ProgressProcess {
	return &ProgressProcess{}
}

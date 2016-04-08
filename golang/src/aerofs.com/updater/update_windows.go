// +build windows

package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
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

func Launch(launcher string, args []string) error {
	if err := PreLaunch(launcher, args); err != nil {
		return err
	}

	// NB: syscall.Exec is not supported on Windows
	cmd := exec.Command(launcher, args...)
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("Could not run launcher: %s %s %v %v", err.Error(), launcher, args, os.Environ())
	}
	return nil
}

func LaunchAero(exec string, forceUpdate bool) error {
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
	current := filepath.Join(approot, "current")
	launcher := filepath.Join(current, settings.launcher)
	args := []string{}

	if !forceUpdate {
		LaunchIfMatching(approot, launcher, args)
	}

	if err := Update(filepath.Join(path, "site-config.properties"),
		settings.manifest,
		approot,
	); err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}
	return Launch(launcher, args)
}

func OpenErrorPage(addr string) error {
	return exec.Command("cmd", "/C", "start", "", "http://"+addr).Run()
}

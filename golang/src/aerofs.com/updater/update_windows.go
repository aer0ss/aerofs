// +build windows

package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
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

// Check Windows major version
// https://msdn.microsoft.com/en-us/library/windows/desktop/ms724439(v=vs.85).aspx
func IsXP() bool {
	dll, err := syscall.LoadDLL("kernel32.dll")
	if err != nil {
		return false
	}
	p, err := dll.FindProc("GetVersion")
	if err != nil {
		return false
	}
	v, _, _ := p.Call()
	dll.Release()
	return byte(v) < 6
}

func Launch(launcher string, args []string) error {
	if err := PreLaunch(launcher, args); err != nil {
		return err
	}

	if err := exec.Command(launcher).Run(); err != nil {
		return fmt.Errorf("Could not run launcher:\n%s", launcher, args, os.Environ(), err.Error())
	}
	return nil
}

func LaunchAero(exec string, forceUpdate bool) error {
	APPDATA := os.Getenv("APPDATA")
	base := filepath.Base(exec)
	path := filepath.Dir(exec)

	var product Product = Client
	if strings.Contains(base, "ts") {
		product = TeamServer
	}

	settings := SETTINGS[product]

	rtroot := filepath.Join(APPDATA, settings.rtroot)
	if err := os.Mkdir(rtroot, 0755); err != nil {
		return fmt.Errorf("Failed to create rtroot:\n%s", err.Error())
	}

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

func OpenErrorPage(addr string) {
	if err := exec.Command("cmd", "/C", "start", "", "http://" + addr).Run(); err != nil {
		log.Println(os.Stderr, err)
	}
}

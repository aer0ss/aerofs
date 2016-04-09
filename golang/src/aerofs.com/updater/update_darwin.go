// +build darwin

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
		launcher: "aerofs",
		manifest: "client-osx.json",
	},
	TeamServer: {
		approot:  "AeroFSTeamServerExec",
		rtroot:   "AeroFS Team Server",
		launcher: "aerofsts",
		manifest: "team_server-osx.json",
	},
}

func Launch(launcher string, args []string) error {
	if err := PreLaunch(launcher, args); err != nil {
		return err
	}

	if err := os.Chdir(filepath.Dir(launcher)); err != nil {
		return err
	}

	if err := syscall.Exec(launcher, args, os.Environ()); err != nil {
		return fmt.Errorf("Could not run launcher:\n%s", launcher, args, os.Environ(), err.Error())
	}
	return nil
}

func LaunchAero(exec string, _ []string) error {
	HOME := os.Getenv("HOME")
	data := filepath.Join(HOME, "Library", "Application Support")
	base := filepath.Base(exec)
	path := filepath.Dir(exec)

	var product Product = Client
	if base == SETTINGS[TeamServer].launcher ||
		strings.Contains(path, "/AeroFSTeamServer.app/") {
		product = TeamServer
	}

	settings := SETTINGS[product]

	rtroot := filepath.Join(data, settings.rtroot)
	if err := setLogFile(rtroot); err != nil {
		return fmt.Errorf("Failed to set logfile:\n%s", err.Error())
	}

	approot := filepath.Join(data, settings.approot)
	current := filepath.Join(approot, "current")
	// NB: GUI loader is in bundle to avoid running afoul of weird OSX restrictions
	launcher := filepath.Join(path, settings.launcher)
	args := []string{launcher, current}

	LaunchIfMatching(approot, launcher, args)

	// run from approot
	properties := filepath.Join(path, "site-config.properties")
	if _, err := os.Stat(properties); err != nil {
		// run from app bundle
		properties = filepath.Clean(path + "../../Resources/site-config.lproj/locversion.plist")
	}

	if err := Update(properties, settings.manifest, approot); err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}
	return Launch(launcher, args)
}

func OpenErrorPage(addr string) error {
	return exec.Command("open", "http://"+addr).Run()
}

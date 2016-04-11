// +build linux

package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
)

var SETTINGS map[Product]settings = map[Product]settings{
	Client: {
		approot:  ".aerofs-bin",
		rtroot:   ".aerofs",
		launcher: "launcher",
		manifest: "client-linux-%s.json",
	},
	TeamServer: {
		approot:  ".aerofsts-bin",
		rtroot:   ".aerofsts",
		launcher: "launcher",
		manifest: "team_server-linux-%s.json",
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
		return fmt.Errorf("Could not run launcher: %s %s %v %v", err.Error(), launcher, args, os.Environ())
	}
	return nil
}

func LaunchAero(exec string, fwd []string) error {
	HOME := os.Getenv("HOME")
	base := filepath.Base(exec)
	path := filepath.Dir(exec)

	var product Product = Client
	if strings.Contains(base, "ts") {
		product = TeamServer
	}

	// gui/cli/sh detection based on symlinking
	prog := "gui"
	idx := strings.LastIndexByte(base, '-')
	if idx != -1 {
		prog = base[idx+1:]
	}

	settings := SETTINGS[product]

	rtroot := filepath.Join(HOME, settings.rtroot)
	if err := setLogFile(rtroot); err != nil {
		return fmt.Errorf("Failed to set logfile:\n%s", err.Error())
	}

	approot := filepath.Join(HOME, settings.approot)
	current := filepath.Join(approot, "current")
	launcher := filepath.Join(current, settings.launcher)
	args := make([]string, len(fwd)+2)
	args[0] = launcher
	args[1] = prog
	if len(fwd) > 0 {
		copy(args[2:], fwd)
	}

	LaunchIfMatching(approot, launcher, args)

	if err := Update(filepath.Join(path, "site-config.properties"),
		fmt.Sprintf(settings.manifest, runtime.GOARCH),
		approot,
	); err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}
	return Launch(launcher, args)
}

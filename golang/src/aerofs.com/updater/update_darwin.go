// +build darwin

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

	env := os.Environ()
	env = append(env, "AERO_APP_PATH="+filepath.Dir(launcher))

	if err := syscall.Exec(launcher, args, env); err != nil {
		return fmt.Errorf("Could not run launcher:\n%s", launcher, args, env, err.Error())
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
	version := LastInstallVersion(approot)
	// NB: GUI loader is in bundle to avoid running afoul of weird OSX restrictions
	launcher := filepath.Join(path, settings.launcher)
	args := []string{launcher, InstallPath(approot, version)}

	LaunchIfMatching(approot, launcher, args)

	// run from approot
	properties := filepath.Join(path, "site-config.properties")
	if _, err := os.Stat(properties); err != nil {
		// run from app bundle
		properties = filepath.Clean(path + "../../Resources/site-config.lproj/locversion.plist")
	}

	inst, err := Update(properties, settings.manifest, approot, version)
	if err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}
	args = []string{launcher, inst}
	return Launch(launcher, args)
}

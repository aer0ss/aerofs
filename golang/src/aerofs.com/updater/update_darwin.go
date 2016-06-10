// +build darwin

package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
)

var SETTINGS map[Product]settings = map[Product]settings{
	Client: {
		approot:  "AeroFSExec",
		rtroot:   "AeroFS",
		launcher: "aerofs",
		manifest: "client-osx.json",
		monitor:  "AeroFSProgressMonitor",
	},
	TeamServer: {
		approot:  "AeroFSTeamServerExec",
		rtroot:   "AeroFS Team Server",
		launcher: "aerofsts",
		manifest: "team_server-osx.json",
		monitor:  "AeroFSTeamServerProgressMonitor",
	},
}

type ProgressProcess struct {
	progress int
	total    int
	cmd      *exec.Cmd
	stdin    io.WriteCloser
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
	path := filepath.Dir(exec)

	product := getProduct(exec)
	settings := SETTINGS[product]

	rtroot := filepath.Join(data, settings.rtroot)
	if err := setLogFile(rtroot); err != nil {
		return fmt.Errorf("Failed to set logfile:\n%s", err.Error())
	}

	approot := filepath.Join(data, settings.approot)
	// NB: GUI loader is in bundle to avoid running afoul of weird OSX restrictions
	launcher := filepath.Join(path, settings.launcher)
	args := []string{launcher, filepath.Join(approot, "current")}

	LaunchIfMatching(approot, launcher, args)

	// run from approot
	properties := filepath.Join(path, "site-config.properties")
	if _, err := os.Stat(properties); err != nil {
		// run from app bundle
		properties = filepath.Clean(path + "../../Resources/site-config.lproj/locversion.plist")
	}

	inst, err := Update(properties, settings.manifest, approot, exec)
	if err != nil {
		log.Printf("Failed to update from site-config:\n\t%s", err.Error())
		if len(inst) > 0 {
			LaunchIfMatching(approot, launcher, args)
		}
		return fmt.Errorf("Failed to update from site-config:\n%s", err.Error())
	}
	args = []string{launcher, inst}
	return Launch(launcher, args)
}

func getProduct(exec string) Product {
	base := filepath.Base(exec)
	path := filepath.Dir(exec)
	var product Product = Client
	if base == SETTINGS[TeamServer].launcher ||
		strings.Contains(path, "/AeroFSTeamServer.app/") {
		product = TeamServer
	}
	return product
}

func (prog *ProgressProcess) Kill() {
	if prog.stdin != nil {
		prog.stdin.Close()
		prog.stdin = nil
	}
	if prog.cmd != nil {
		log.Printf("Killing progress monitor")
		if err := prog.cmd.Process.Kill(); err != nil {
			log.Printf("Failed to kill progress monitor")
		}
		prog.cmd = nil
	}
}

func (prog *ProgressProcess) Launch() {
	stdin, err := prog.cmd.StdinPipe()
	if err != nil {
		log.Printf("Failed to configure pipe to progress monitor")
		return
	}

	err = prog.cmd.Start()
	if err != nil {
		log.Printf("Failed to launch progress monitor")
		return
	}
	prog.stdin = stdin
}

func (prog *ProgressProcess) IncrementProgress(increment int) {
	prog.progress = prog.progress + increment
	if prog.stdin != nil && prog.cmd != nil {
		io.WriteString(prog.stdin, strconv.Itoa(prog.progress)+"\n")
	}
	if prog.progress >= prog.total {
		prog.Kill()
	}
}

func NewProgressMonitor(total int, execString string) *ProgressProcess {
	path := filepath.Dir(execString)
	product := getProduct(path)
	settings := SETTINGS[product]
	cmd := exec.Command(filepath.Join(path, settings.monitor), strconv.Itoa(total))
	return &ProgressProcess{
		total:    total,
		progress: 0,
		cmd:      cmd,
	}
}

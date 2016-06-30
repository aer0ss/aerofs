// +build linux

package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
)

var SETTINGS map[Product]settings = map[Product]settings{
	Client: {
		approot:  ".aerofs-bin",
		rtroot:   ".aerofs",
		launcher: "launcher",
		manifest: "client-linux-%s.json",
		monitor:  "aerofsprogressmonitor",
	},
	TeamServer: {
		approot:  ".aerofsts-bin",
		rtroot:   ".aerofsts",
		launcher: "launcher",
		manifest: "team_server-linux-%s.json",
		monitor:  "aerofstsprogressmonitor",
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

	if err := syscall.Exec(launcher, args, os.Environ()); err != nil {
		return fmt.Errorf("Could not run launcher: %s %s %v %v", err.Error(), launcher, args, os.Environ())
	}
	return nil
}

func LaunchAero(exec string, fwd []string) error {
	HOME := os.Getenv("HOME")
	base := filepath.Base(exec)
	path := filepath.Dir(exec)

	product := getProduct(exec)

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
	launcher := filepath.Join(approot, "current", settings.launcher)
	args := make([]string, len(fwd)+2)
	args[0] = launcher
	args[1] = prog
	if len(fwd) > 0 {
		copy(args[2:], fwd)
	}

	LaunchIfMatching(approot, launcher, args)

	// convert GOARCH value if needed
	arch := runtime.GOARCH
	if arch == "386" {
		arch = "i386"
	}

	inst, err := Update(filepath.Join(path, "site-config.properties"),
		fmt.Sprintf(settings.manifest, arch),
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

	launcher = filepath.Join(inst, settings.launcher)
	args[0] = launcher
	return Launch(launcher, args)
}

func getProduct(exec string) Product {
	base := filepath.Base(exec)
	var product Product = Client
	if strings.Contains(base, "ts") {
		product = TeamServer
	}
	return product
}

func (prog *ProgressProcess) Kill() {
	if prog.stdin != nil {
		prog.stdin.Close()
		prog.stdin = nil
	}
	if prog.cmd != nil && prog.cmd.Process != nil {
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
		log.Printf("Failed launch progress monitor:\n\t%s", err.Error())
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
	arch := runtime.GOARCH
	if arch == "386" {
		arch = "i386"
	}
	monitor := settings.monitor + "-" + arch
	cmd := exec.Command(filepath.Join(path, monitor), strconv.Itoa(total))
	return &ProgressProcess{
		total:    total,
		progress: 0,
		cmd:      cmd,
	}
}

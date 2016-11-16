// +build windows

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
		launcher: "aerofs.exe",
		manifest: "client-win.json",
		monitor:  "aerofsprogressmonitor.exe",
	},
	TeamServer: {
		approot:  "AeroFSTeamServerExec",
		rtroot:   "AeroFSTeamServer",
		launcher: "aerofsts.exe",
		manifest: "team_server-win.json",
		monitor:  "aerofstsprogressmonitor.exe",
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
	path := filepath.Dir(exec)

	product := getProduct(exec)

	settings := SETTINGS[product]

	LOCALAPPDATA := os.Getenv("LOCALAPPDATA")
	if len(LOCALAPPDATA) == 0 {
		return fmt.Errorf("LOCALAPPDATA not set")
	}
	rtroot := filepath.Join(LOCALAPPDATA, settings.rtroot)
	if err := setLogFile(rtroot); err != nil {
		return fmt.Errorf("Failed to set logfile:\n%s", err.Error())
	}

	// NB: run this check after the log file has been located so that logging can occur
	EnforceSingleInstance()

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
		log.Printf("Failed to configure pipe to progress monitor:\n\t%s", err.Error())
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
	monitor := settings.monitor
	cmd := exec.Command(filepath.Join(path, monitor), strconv.Itoa(total))
	return &ProgressProcess{
		total:    total,
		progress: 0,
		cmd:      cmd,
	}
}

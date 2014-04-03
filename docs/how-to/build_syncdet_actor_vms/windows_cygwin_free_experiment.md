This document describes a failed attempt to remove the dependency of cygwin for Windows SyncDET actors. It also propose how we should move forward.

# Why the attempt

There have been complains about Windows VMs. In particular,

- Sometimes cygwin messes up with Windows networking and DNS settings.
- Sometimes `vagrant up` fails to set proper hostnames. The symptom is that the box is not reachable via the regular hostname `{userid}-win7-vagrant-0`. After logging in via the IP address, the `hostname` command shows `winvagrant`. This issue may not be related to cygwin.

# The attempt and proposal for next steps

I (WW) apptempted to solve the problem by replacing cygwin with other tools, but without great success. We concluded that the best next solution would be to use no 3rd-party tools, but run a Python server on the actors. The server copies files, creates folders, manages subprocesses, and run other Python programs for the controller. This would minimize dependency on other libraries.

# Setup details

This section describes the steps I performed for the attempt.

## Install Python

- Install Python 2.7 for Windows
- Add C:\Python27 to PATH

## Install MSYS for bash

- Download the MinGW Installer, and install MSYS from the installer. http://www.mingw.org/
- Add C:\MinGW\msys\1.0\bin to PATH

## Install cwRsync for rsync

- Install the free edition of cwRsync. https://www.itefix.no/i2/content/cwrsync-free-edition
- Add cwRsync's path to PATH.

Alternatively, install rsync from the MinGW Installer. However, at the time this doc was written, MinGW's rsync is at version 3.0.8, which is incompatible with brew's rsync (3.1.0).

## Install FreeSSHd for sshd

- Download and install FreeSSHd at http://www.freesshd.com/freeSSHd.exe
  - When prompted, allow it to create the private key
  - When prompted, allow it to run as a service.
- Run the FreeSSHd app. If you attempts to start sshd from there, you will see an error saying that the port is already in use. It's because sshd is already running as a service.
- In the "SSH" tab, set the command line shell as C:\MinGW\msys\1.0\bin\sh.exe
- In the "Users" tab, add a user `vagrant` with password `vagrant`.
- Add another user `aerofstest`. Use "Public key" authentication.
- Save the user's public keys as a file at %PROGRAMFILES%\freeSSHd\aerofstest.
- Copy C:\Users\aerofstest\AppData\Local\VirtualStore\Program Files\freeSSHd\FreeSSHDService.ini to C:\Program Files\freeSSHd\ and overwrite the existing one. The form one is used by sshd in the app, and the latter is used by the sshd as a service.
- Restart the service with command `net stop FreeSSHDService` followed by `start`.

Note: Sometime you need to unload FreeSSHd (by selecting "Unload" from the tray menu icon) before changes can take effect.

## Install newer rsync on the controller

Install rsync 3 on the host OS (where the controller runs) if the local rsync command complains version mismatch:

    brew tap homebrew/dupes
    brew install rsync
    
# Issues with the attempted approach

rsync hangs with the following command run by the host:

    rsync -vv --archive test {ip_address}:/test
    
Using Windows Process Explorer I found that the rsync process in the VM started, but doesn't have listening ports. I didn't figure out why.

A non-blocking issue: FreeSSHd doesn't work well in interactive mode: 1) It screws up screen output. 2) When freeSSHd runs as a service, sh.exe and some other MSYS commands take a long time to execute. I suspect it's caused by the admin user account on which the service runs (use `whoami` to show the user id). However, they're not big problems as SyncDET remote commands are non-interactive.

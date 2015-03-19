## Overview

local-prod is a developer testing tool. Its goal is to make testing AeroFS clients and servers painless, reliable and fast.

## System requirements

- vagrant (version 1.2.x upwards)
- VirtualBox (version 4.2.6)

You should already have installed them as per the [Getting Started guide](../how-to/get-started.html).

## Initial setup

Start by adding this to your `~/.profile` or `~/.bashrc` if you're on Linux or OSX:

    source ~/repos/aerofs/tools/bashrc/include.sh

This will give you access to all the local production aliases. All local production commands start with `lp-`, so you can type `lp-<tab>` in your shell to see all available commands.

    roncy:~$ lp-
    lp-create  lp-destroy lp-deploy  lp-halt    lp-ssh     lp-start

## Workflow

A typical workflow is as follows:

     +----------------+
     |                |
     |   lp-create    |
     |                |
     +-------+--------+
             |
     +-------v--------+
     |                |
     |    lp-start    +-------------+
     |                |             |
     +-------A--------+             |
             |             +--------v--------+
             |             |     lp-ssh      |
             |             |    lp-deploy    |
             |             |     lp-kick     |
             |             |       ...       |
             |             +--------+--------+ 
    +--------v--------+             |
    |                 |             |
    |    lp-halt      <-------------+
    |                 |
    +--------+--------+
             |
    +--------v--------+
    |                 |
    |    lp-destroy   |
    |                 |
    +-----------------+

You will generally setup your local-prod environment once, start/stop/ssh/deploy to it multiple times, and (if all hell breaks loose), destroy it and start all over again.

## Commands

### Setup the local-prod machine

    lp-create [<iface>]

See the [Networking](#networking) section for an explanation of `<iface>`.

Beware of having no multithreading flag for make set (`MAKEFLAGS="-j8"` for example), otherwise you will need to `export MAKEFLAGS="-j1"`.

This command:

1. Builds all the deb packages and uploads them to your repo on apt.aerofs.com
2. Deletes all existing local-prod boxes (so that you start from a clean slate)
3. Creates and provisions the local-prod boxes
4. Starts the local-prod boxes

By default the local-prod machine is only reachable on your local machine, unless you specify the `<iface>` parameter. The default IP (to be added to `/etc/hosts`) is:

    192.168.51.150 share.syncfs.com

### Start your local-prod machine
 
    lp-start [<iface>]

See the [Networking](#networking) section for an explanation of the `<iface>` parameter.

### Halt (stop) your local-prod machine

    lp-halt

### Redeploy (i.e. refresh) the local-prod virtual machine

    lp-kick

This command:

1. Builds all the deb packages
2. Re-provisions the local-prod virtual machine

### SSH to a running local prod virtual machine

    lp-ssh

## Networking
<a name="networking" />

In most cases, the local prod VMs do not need to be accessible outside the host machine. In that case a host-only network interface can be used. **Avoid bridging** unless you absolutely have to. The host-only (static IP) configuration is more resilient, and easier to reason about and debug.

However, when doing multi-devices tests (e.g. with the Android app) the servers need to be reachable from other machines. For that, the VMs need to be configured to use a bridged network interface. To specify such an interface, an optional parameter can be passed to the setup and start commands:

    lp-create [<iface>]
    lp-start  [<iface>]

`<iface>` should be an interface on your local machine. For example, `en0`, `en1`, `bbtp`...

### IMPORTANT note about bridged interfaces

Bridged IPs can change every time the bridge interface goes up/down or the DHCP lease is renewed. To avoid hard-to-diagnose issues:

1. `lp-stop` and `lp-start` your local-prod machine to ensure that vagrant picks up the correct bridged ips
2. Update the /etc/hosts entries on your remote machines to point to the new local-prod IPs

## AeroFS Services

All AeroFS services are now run on `share.syncfs.com`. The admin panel is available at [https://share.syncfs.com](https://share.syncfs.com).

## Running AeroFS Clients

To run AeroFS clients that connect to your local-prod virtual machine, run the following:

    ant setupenv -Dmode=PRIVATE -Dproduct=<CLIENT|TEAM_SERVER>

## Using SyncDET with Local Production

Please see [[Setup SyncDET to use a Local Production Environment]] to make sure SyncDET talks to Local Production.

## Known issues

- If you switch your bridged network interface or drop off the VPN while local-prod is up, you might get random networking-related failures
- The local prod image uses an old version of the linux kernel, so Mac OS X repackaging does not work.

## Bugs, complaints and feature requests

Please report issues to Matt <matt@aerofs.com>. These include cases where local-prod fails to setup, where it looks like each individual setup succeeds but setup fails later on, where local-prod fails to start properly, where it takes too long, or... Nothing is too big or too small to report!

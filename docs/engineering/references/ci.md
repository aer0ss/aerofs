# Introduction

The purpose of this doc is to provide an explanation of the CI infrastructure.
This includes the appliance, the agents, the actors, and the tests.

If CI has died and a new one must be provisioned, this may be done via ansible.
*DO NOT* refer to obsolete docs about CI setup that do not mention ansible.

# CI

We run a continuous integration test system with TeamCity. Once upon a time we had
two branches being tested concurrently (Amium @ `master` and AeroFS @ `maintenance_aerofs_private`)
These days, only the legacy AeroFS codebase goes through CI, and only infrequently when
a customer issue requires a maintenance update. Vestigial artificats of this setup
remain although they should not be visible in the default web interface.

A full suite of tests are run on every commit to `maintenance_aerofs_private` branch of this
Github repo.

## TeamCity

The TC controller is hosted on an m3.large on AWS. It could conceivably be turned on/off as
needed to save money until a CI run is necessary. However this would require anyone in charge
of starting a build to have access to the AWS console, know which box to start, and wait for
it to come up, which would be brittle and annoying.

The web interface is available at [https://libellule.arrowfs.org](https://libellule.arrowfs.org),
which is only reachable on the AeroFS VPN.

Each employee has their own login to TeamCity. New logins need to be created manually as needed
by one of the admins:

 - Hugues
 - Matt
 - Elvis

Likewise, old account should be purged as employees leave, although this is not much of a security
concern given that CI is not accessible outside of the AeroFS VPN.

## Agents

The TC controller is merely an orchestrator and doesn't run any tests itself. In TC lingo, this
happens on "agents". We use the following machines as TC agents:

 - muscle.libellule.arrowfs.org
   - m3.large CoreOS box
   - stopped by default and brought up as needed when a CI run triggers
   - used by end-to-end system tests as an AeroFS appliance
 - cloudagent1.libellule.arrowfs.org
   - m3.large CoreOS box
   - stopped by default and brough up as needed when a CI run triggers
   - runs java unit tests which rely on the availability of redis/mysql
 - buildmachine.arrowfs.org
   - macOS laptop in the office mezzanine
   - responsible for final signed build (only possible on macOS because xcode)
 - smallguy.arrowfs.org
   - ubuntu tower in the office mezzanine
   - runs syncdet system tests
   - runs "Nightly" build/test VM task
 - bigboy.arrowfs.org
   - ubuntu tower in the office mezzanine
   - run syncdet system tests
   - hosts all [actor VMs](#actors)

### Arcane details

The current setup to start/stop muscle/cloudagent1 on demand works fine most of the time but it
has a few peculiarities worth mentioning.

First of all, TC does not make it possible to do *anything* on the controller. This unfortunately
means that the task of calling into the EC2 API to start/stop these instances has to happen on a
TC agent, which, for obvious reasons, cannot be either of the the ones being started/stopped.
For simplicity that repsonsibility has been assigned to smallguy as it is ansible-provisioned,
unlike the other two physical agents. The AWS creds are in the Vault and loaded onto the agent
via ansible.

More importantly, TC does not offer any straightforward way of preventing multiple build chains
from being interleaved. This leaves open the possibility of two subsequent commits triggering
two builds:

 1. start build N : start muscle/cloudagent1
 2. start build N+1 : start muscle/cloudagent1 noop
 3. finish build N : stop muscle/cloudagent1
 4. build N+1 breaks/stalls due to stopped agent(s)

To prevent this, one would have to manually curate the TC build order when multiple commits are
pushed in close succession.

To remedy this, one would have to cancel all remaining tasks in a stalled/broken run and trigger
a fresh CI run.


## Network Setup

All actors are on the VPN.

bigboy and smallguy have a more complicated setup to make actors VMs accessible.
Specifically, they have to be on the smae LAN, which is NAT'd by the bigboy box.

bigboy has two physical network adapters; one connects to the world, and the other connects to
a switch through which other computers involved in CI connect.
All the VM actors currently live on bigboy and bridge to the NAT adapter.

bigboy runs an instance of dnsmasq which is used as a DNS and DHCP server for the CI LAN.
NB: this dnsmasq instance delegates DNS resolution to the VPN DNS, which is necessary for
agents to reach the TC controller and actors to reach the appliance on muscle.

NB: make sure dnsmasq does NOT listen on the docker0 bridge interface or the containerized dns
will fail to start

in /etc/dnsmasq.conf
```
except-interface=docker0
bind-interfaces
```

Restart dnsmasq to pick up config changes if needed: `sudo service dnsmasq restart`

The network sometimes gets into a weird state wherein one (or both) of bigboy/smallguy fails
to resolve DNS to VPN and/or the outside world. This is usually fixed by restarting dnsmasq


## Actors

Syncdet system tests install one or more desktop clients and simulate user interaction.
These clients are installed on "actors" of the following types

 - Ubuntu VMs, hosted on bigboy
 - Windows 7 VMs, hosted on bigboy
 - macOS machine, in the office mezzanine, 10.0.10.190 on bridged network (from bigboy/smallguy)

All actors are configured to be reachable by passwordless ssh for `aerofstest` from bigboy/smallguy
This is how syncdet is able to execute the necessary commands on them to install a clients,
simulate user interaction, verify expectation, and report results.

## Systest-Setup and the Actor Pool

Syncdet tests require a YAML-formatted config file containing configuration values, actor addresses,
teamserver details, and AeroFS credentials.

`tools/ci/systest-setup.py` is a script which performs setup steps (user creation and administration,
clearing of the S3 bucket, etc.) and generates this syncdet YAML file.

The main input of the script is an actor profile, which lists qualities of the actors necessary for
a test. For instance, a test requiring two isolated actors, one running Linux and one running
Windows, would specify a profile like the following:

    actors:
      - os: linux
        isolated: true
      - os: linux
        isolated: true

The script will then contact the actor pool service (see `tools/ci/actor-pool`) running on bigboy.
The service will return the addresses of actors that are available to run tests and that meet the
criteria specified in the profile. When the tests are completed, `tools/ci/systest-cleanup.py` is
invoked on the generated syncdet config to return the actors to the pool.

The pool is populated by the "Setup Actors" task on the TC controller, which is triggered weekly,
to ensure that the VM images do not grow without bounds (virtualbox is not very good at shrinking
file system images after large write/delete and some system tests do not fully clean up after
themselves) and to avoid weird network glitches.

This needs to be triggered manually after bigboy is restarted, as VMs do not restart automatically
and the actor pool states does not survive a reboot.

NB: in some rare cases, network glitches and or TC glitches can leave the actor pool in a bad state
even if the VMs themselves are in a good state; or a subset of VMs might end up in a bad state.
When this happens, manually triggering "Actors Setup" is usually enough to bring things back to a
sane state.


## How to add a new suite of System Tests

Every system test has the same seven steps, inherited from the "System Tests" template. They differ
only in the values of four "Build Parameters". The easiest way to create a new System Test is to
duplicate an existing test and modify these five parameters (most notably the "Setup Conf File",
which is the input to `ci-systest-setup.py`, and "Syncdet Scenario", which lists the syncdet tests
that should be run).


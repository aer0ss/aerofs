# Introduction

The purpose of this doc is to provide an explanation of the CI infrastructure. This includes the appliance, the agents, the actors, and the tests. If CI has died and a new one must be constructed, see docs/how-to/ci_setup.md

# CI

We run a continuous integration test system with TeamCity. A full suite of tests are run on every commit to `master` branch of our Gerrit repo.

## TeamCity

The web interface is available at [https://newci.arrowfs.org:8543](https:newci.arrowfs.org:8543) (accessible on the VPN) or [https://192.168.128.197:8543](https://192.168.128.197:8543) on the LAN.

Every employee has his/her own login to TeamCity. Jon Pile set this up, and he should probably be bothered if new accounts need to be created.


## Network Setup

Everything is on a LAN that is NAT'd by the CI box.

CI has two physical network adapters; one connects to the world, and the other connects to a switch through which other computers involved in CI connect. All the VM actors currently live on CI and bridge to the NAT adapter.

CI runs an instance of dnsmasq which is used as a DNS and DHCP server for the CI LAN. Boxes can be referred to by hostname on this network, which is nice.


## Build Agents

Our license with TeamCity allows us to use three build agents.

One agent lives on the CI box, and is the default agent that ships with TeamCity. His name is `linux-physical-teamcity-bundled`. Only he can run the `Private Deployment Setup` step, to ensure that the appliance always lives in the same place.

A second agent lives on a separate linux desktop on the CI network. His name is `linux-physical`. Only he can run the `Build and Unit Test` step, because historically, the main CI box was so overloaded that some tests would fail sporadically.

There were once virtual agents, spawned from the `agent-vagrant` repo, but they were discontinued because some tests ran slowly on them.


### Build Agent Disconnecting Flakiness

N.B. this section is only relevant to the virtual agents, which are not in use as of May 2014. The information will stay in this doc in case the virtual agents become used in the future.

Sometimes, one of the agents will freeze up and be unable to fulfill his duties as a build agent. Restarting him is sufficient to fix this. To remove the human element, a script called `monitor_agents.py` in the `misc-tools` repo is set to run every two minutes (see `/etc/crontab`). It unauthorizes disconnected agents, authorizes connected agents, and restarts `agent1` and/or `agent2` if they are not connected.

## SyncDET Actors

### Linux Actors

These actors come from `tools/vagrant/syncdet_linux`. Multiple actors can be brought up by running:

`CLIENT_COUNT=5 BRIDGE_IFACE='eth0' BRIDGE_COUNT=2 vagrant up`

In this example, a total of five actors will be created, the first two of which will have bridged and host-only interfaces, and the latter three of which will have only host-only interfaces (and are thus isolated from the rest of the world).

The hostonly IP's have the form 192.168.50.#{index+10}, where index ranges from 0 to CLIENT_COUNT-1.

The bridged IP's can be found by running `./list_bridged_ips.sh`

### Windows Actors

These actors come from `tools/vagrant/syncdet_win`. Multiple actors can be brought up by running:

`WCLIENT_COUNT=5 BRIDGE_IFACE='eth0' WBRIDGE_COUNT=2 vagrant up`

The hostonly IP's have the form 192.168.50.#{index+110}.

The bridged IP's can be found by running `./list_bridged_ips.sh`

### OSX Actors

There is a mac mini with the hostname `ci` on the CI network. There are no OSX VM's.


## Systest-Setup and the Actor Pool

Syncdet tests require a YAML-formatted config file containing configuration values, actor addresses, teamserver details, and AeroFS credentials.

`tools/ci/systest-setup.py` is a script which performs setup steps (user creation and administration, clearing of the S3 bucket, etc.) and generates this syncdet YAML file.

The main input of the script is an actor profile, which lists qualities of the actors necessary for a test. For instance, a test requiring two isolated actors, one running Linux and one running Windows, would specify a profile like the following:

    actors:
      - os: linux
        isolated: true
      - os: linux
        isolated: true

The script will then contact the actor pool service (see `tools/ci/actor-pool`) running on CI. The service will return the addresses of actors that are available to run tests and that meet the criteria specified in the profile. When the tests are completed, `tools/ci/systest-cleanup.py` is invoked on the generated syncdet config to return the actors to the pool.


## How to add a new suite of System Tests

Every system test has the same seven steps, inherited from the "System Tests" template. They differ only in the values of four "Build Parameters". The easiest way to create a new System Test is to duplicate an existing test and modify these five parameters (most notably the "Setup Conf File", which is the input to `ci-systest-setup.py`, and "Syncdet Scenario", which lists the syncdet tests that should be run).

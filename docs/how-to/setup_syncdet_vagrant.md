See also [setup_ci_1](setup_ci_1.html), [setup_ci_2](setup_ci_2.html), [setup_syncdet_actors](setup_syncdet_actors.html), [setup_syncdet](setup_syncdet.html) (in progress). **TODO: consolidate them.**


syncdet-vagrant
===============

Vagrant config for creating syncdet-ready vms

Prerequisites
-------------

 1. Install VirtualBox
 2. Install Vagrant >= 1.2.x


Setup
-----

    [BRIDGE_IFACE="en1"] [BRIDGE_COUNT=1] CLIENT_COUNT=2 vagrant up

The `CLIENT_COUNT` env var specifies the number of vms you want to setup.

The `BRIDGE_IFACE` env var specifies which interface to use for bridging (to
list possibilities run `VBoxManage list bridgedifs`). Leave it empty to use
hostonly networking.

The `BRIDGE_COUNT` env var allows the user to assign bridged adapters to only a subset of
the actors. If `CLIENT_COUNT=5` and `BRIDGE_COUNT=3`, then the first 3 actors will have
bridged interfaces and the last 2 won't.

Vagrant will create the vms in the `~/.vagrant.d` directory. If you want to store them in 
another place, you may simply symlink `~/.vagrant.d` to any other place you want to use.

Once vagrant is done setting up the vms, there will be a `config.yaml` file in the current 
directory. Copy or merge this file with your current `/etc/syncdet/config.yaml` and you're
all set.


A sample YAML file
------------------
```
actor_defaults:
  aero_userid: jonathan+testwin@aerofs.com
  aero_password: temp123
  aero_host: unified.syncfs.com
  login: aerofstest
  root: ~/syncdet
  rsh: ssh
actors:
- address: jonathang-vagrant-0.local
- address: jonathang-vagrant-1.local
```

SSH Access
----------

The vagrant script automatically copies your ssh public key (from `~/.ssh/id_rsa.pub`)
to the vm as syncdet needs passwordless login.

The vms are given names of the form `<username>-vagrant-<index>.local`, where `<username>` 
is the output of whoami on the host and `<index>` is an integer in [0..CLIENT_COUNT-1]

Regular ssh access:

    $ ssh aerofstest@<username>-vagrant-<index>.local

For sudo access, use the vagrant account:

    $ ssh -i ~/.vagrant.d/insecure_private_key vagrant@<username>-vagrant-<index>.local

Note: Not recommended unless you make your changes permanent by updating the puppet  manifest.

Helpers
-------

    ./list_bridged_ips.sh

If using bridged networking, this script will retrieve the bridged IPs of all
running vms.

Troubleshooting
---------

If puppet complains that `/usr/bin/apt-get update returned 100` on an actor,
then said actor's networking may be borked. `vagrant ssh <actor name> -c "sudo
rm -rf /var/lib/dhcp/*"` seems to help.

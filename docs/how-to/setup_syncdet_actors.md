
This article describes how to create actor VMs for SyncDET. It assumes you've setup the development environment and the local prod by following [this doc](get_started.html).

Create actor VMs
---

Checkout SyncDET:

    $ cd ~/repos
    $ git clone ssh://gerrit.arrowfs.org:29418/syncdet

Copy the sample yaml file to /etc/syncdet:

    $ sudo mkdir /etc/syncdet
    $ sudo cp syncdet/config.yaml.sample /etc/syncdet

Go to the syncdet-vagrant foler:

    $ cd ~/repos/aerofs/tools/syncdet-vagrant
    
Create a hostonly setup:

    $ CLIENT_COUNT=2 vagrant up
    
Or, create a bridge networking setup:
    
    $ CLIENT_COUNT=2 BRIDGE_COUNT=1 BRIDGE_IFACE="en1"

- `CLIENT_COUNT` specifies the number of VMs you want to setup. It must be 2 or greater.

- `BRIDGE_IFACE` specifies which interface to use for bridging (to
list possibilities run `VBoxManage list bridgedifs`). Leave it empty to use
hostonly networking.

- `BRIDGE_COUNT` allows the user to assign bridged adapters to only a subset of
the actors. If `CLIENT_COUNT=5` and `BRIDGE_COUNT=3`, then the first 3 actors will have
bridged interfaces and the last 2 won't.

Vagrant will create the VMs in the `~/.vagrant.d` directory. If you want to store them in 
another place, you may simply symlink `~/.vagrant.d` to any other place you want to use.

Connect the VMs to SyncDET
---

Create a file `/etc/syncdet/config.yaml` with the following content:

```
actor_defaults:
  aero_userid: {aero_user_id}
  aero_password: {aero_password}
  aero_host: unified.syncfs.com
  login: aerofstest
  root: ~/syncdet
  rsh: ssh
actors:
- address: {username}-vagrant-0.local
- address: {username}-vagrant-1.local
```

Replace `{aero_user_id}` and `{aero_password}` with an account in your local prod, and `{username}` is the output of `whoami` on your localhost.

SSH access
---

The vagrant script automatically copies your ssh public key from `~/.ssh/id_rsa.pub`
to the VM as syncdet needs passwordless login.

The vms are given names of the form `{username}-vagrant-{index}.local`, where `{index}` is an integer in [0..CLIENT_COUNT-1].

To log in to the VM:

    $ ssh aerofstest@{username}-vagrant-{index}.local

For sudo access, use the vagrant account:

    $ ssh -i ~/.vagrant.d/insecure_private_key vagrant@{username}-vagrant-{index}.local

Sudo access is not recommended unless you make your changes permanent by updating the puppet manifest.

Useful tools
---

- Use this command to launch, shutdown, and demolish the VMs:

        $ cd ~/repos/aerofs/tools/syncdet-vagrant
        $ CLIENT_COUNT=2 vagrant {up,halt,destroy}

- If using bridged networking, this script retrieves the bridged IPs of all running vms:

        ./list_bridged_ips.sh

Troubleshooting
---

If puppet complains that `/usr/bin/apt-get update returned 100` on an actor,
then said actor's networking may be borked. `vagrant ssh {actor name} -c "sudo
rm -rf /var/lib/dhcp/*"` seems to help.


This article describes how to set up SyncDET actor VMs on your development computers. It assumes you've setup the local prod following the [Getting Started guide](get_started.html).

Set up Linux actors
---

Copy the sample yaml file in the syncdet repo to /etc/syncdet

*TODO (JG): remove the code in the Vagrantfile that attempts to read /etc/syncdet/config.yaml and stop worrying about this. If we really want to generate yaml files automatically, let's generate one from scratch instead of assuming you already have one and trying to modify it.*

    $ sudo mkdir /etc/syncdet
    $ sudo cp ~/repos/syncdet/config.yaml.sample /etc/syncdet

Go to the vagrant foler:

    $ cd ~/repos/aerofs/tools/vagrant/syncdet_linux
    
Create a hostonly setup:

    $ CLIENT_COUNT=2 vagrant up

Or, create a bridge networking setup (not recommended for beginners):
    
    $ CLIENT_COUNT=2 BRIDGE_COUNT=2 BRIDGE_IFACE="en1"

- `CLIENT_COUNT` specifies the number of VMs you want to setup. It must be 2 or greater.

- `BRIDGE_IFACE` specifies which interface to use for bridging (to
list possibilities run `VBoxManage list bridgedifs`). Leave it empty to use
hostonly networking.

- `BRIDGE_COUNT` allows the user to assign bridged adapters to only a subset of
the actors. If `CLIENT_COUNT=5` and `BRIDGE_COUNT=3`, then the first 3 actors will have
bridged interfaces and the last 2 won't.

The first time you run the command, it will download a vagrant box, which we store on the CI computer in the office's local network. The box is fairly large (a few GB), so get yourself a cup of coffee.

If you are not in the office's physical LAN, you can still download the box by editing the Vagrant file to replace the hostname in `box_url` to `newci.arrowfs.org` (in which case, get yourself two cups of coffee and a red-bull).

Vagrant will create the VMs in the `~/.vagrant.d` directory. If you want to store them in 
another place, you may simply symlink `~/.vagrant.d` to any other place you want to use.

Set up Windows actors
---

[ *Running Windows actors on development computers are not officially supported and are likely to fail.* ]

The steps are identical to setting up Linux actors, except that:

- Install the vagrant-windows plugin before running `vagrant up`:
  - Check out the source code at [https://github.com/WinRb/vagrant-windows](https://github.com/WinRb/vagrant-windows).
  - Follow instructions in the section *Installing Vagrant-Windows From Source* of README.md to install the plugin. Ignore the rest of the README.
- use the `syncdet_win` folder instead of `syncdet_linux`
- use `WCLIENT_COUNT` in place of `CLIENT_COUNT`
- use `WBRIDGE_COUNT` in place of `BRIDGE_COUNT`


Configure SyncDET to use the actors
---

Overwrite the file `/etc/syncdet/config.yaml` with the following content:

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

Replace `{aero_user_id}` and `{aero_password}` with an account in your local prod, and `{username}` is the output of `whoami` on the host computer.

SSH access
---

The vagrant script automatically copies your ssh public key from `~/.ssh/id_rsa.pub`
to the VM as syncdet needs passwordless login.

The vms are given names of the form `{username}-vagrant-{index}.local`, where `{index}` is an integer in [0..CLIENT_COUNT-1].

To log in to the VM:

    $ ssh aerofstest@{username}-vagrant-{index}.local

For sudo access:

    $ ssh -i ~/.vagrant.d/insecure_private_key vagrant@{username}-vagrant-{index}.local

Note: the above command is for Linux actors. For Windows actors, you may need to use `aerofstest` as the user id instead of `vagrant` (to be verified).

or, from the directory containing the Vagrantfile:

    $ vagrant ssh {username}-vagrant-{index}

Sudo access is not recommended unless you make your changes permanent by updating the puppet manifest.

Useful tools
---

- Use this command in a vagrant folder to launch, shutdown, and demolish vagrant VMs:

        $ CLIENT_COUNT=2 vagrant {up,halt,destroy}

- If using bridged networking, this script retrieves the bridged IPs of all running vms:

        ./list_bridged_ips.sh

Troubleshooting
---

If puppet complains that `/usr/bin/apt-get update returned 100` on an actor,
then said actor's networking may be borked. `vagrant ssh {actor name} -c "sudo
rm -rf /var/lib/dhcp/*"` seems to help.

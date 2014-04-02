## Install the vagrant-windows plugin

- Check out the source code at [https://github.com/WinRb/vagrant-windows](https://github.com/WinRb/vagrant-windows).
- Follow instructions in the section *Installing Vagrant-Windows From Source* of README.md to install the plugin. Ignore the rest of the README.

## Bringing up a box

Run:

    WCLIENT_COUNT=2 vagrant up

The first time you run this, it will download a box, which we store on CI.
The box is 6GB, so get yourself a cup of coffee.

The box url uses newci's local address by default, but if you are not on the
physical LAN, you can still download the box by editing the vagrantfile to
specify the box_url at newci.arrowfs.org instead of 192.168.128.197 (in which
case, get yourself two cups of coffee and a red-bull).

## Customizing "vagrant up"

There are three environment variables with which you can control:

1. the number of clients you bring up (WCLIENT_COUNT=x)
2. the number of those clients that use bridged networking (WBRIDGE_COUNT=y)
3. the host interface to use for the bridge (BRIDGE_IFACE=eth0)

## SSH Access

Passwordless login to aerofstest (who is admin) is available using the insecure
key. Run `ssh -i ~/.vagrant.d/insecure_private_key aerofstest@[box-address]`

## Use with Local Prod

The agents will use their host's DNS resolution, so if you have an entry for
unified.syncfs.com in your /etc/hosts, the agents should be able to resolve
it with no extra work.

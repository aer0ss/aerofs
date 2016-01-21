
Before you start, read [this doc](http://docs.vagrantup.com/v2/boxes/base.html) to understand the basics of building Vagrant base boxes.

(See [windows](windows.html), [osx](osx.html) for OS specific instructions.)

# Create a new VirtualBox VM

- Add a Host-only network adapter (aka NIC) named "vboxnet1", in addition to the default NAT adapter.
- After the OS is installed, create the first OS user "aerofstest" with password "temp123." This is the convention used across the AeroFS test infrastructure.
- Use an arbitrary hostname. Vagrantfiles will overwrite it in later stages.
- You should see two NICs in the OS, one assigned to address 10.0.2.15. This is the NAT adapter and the addres points to the host (according to JGray). Manually assign the other NIC (the vboxnet1 adapter) a 192.168.50.* address (e.g. 192.168.50.222).

# Install software

- Install Virtualbox guest additions: http://www.virtualbox.org/manual/ch04.html

Through cygwin, homebrew, or apt-get, install: openssh, vim, git, rsync, python2.7, curl, netcat.

Install pip. Copy and paste the following into your terminal:

    curl https://bootstrap.pypa.io/get-pip.py | python
    sudo pip install protobuf pyyaml requests


# Authorize ssh access

The controller needs passwordless login to all actors. Append the controller's public key (`~/.ssh/id_rsa.pub`) to the actor's authorized keys file (`~/.ssh/authorized_keys`)

After doing this, ssh from the controller to the actor. This accomplishes two things: it (a) ensures that passwordless login is configured properly, and (b) adds the actor's public key to the controller's known_hosts file.

# Set up DNS

By default, sp.aerofs.com points to our production SP server. This is almost certainly not what you want. It is most likely that you want to run SyncDET tests in one of two environments:

## DNS for Local Prod

Use syndet-vagrant (source: tools/syncdet-vagrant*) to generate SyncDET actor VMs. These should work out of box.

## DNS for CI

CI actors need access to the VPN. Follow [these instructions](../references/vpn.html) to set up VPN.

You will have two ethernet adapters in your network settings. One is for TUN/TAP and will likely have in IP in  the 172.19.10.0/24 block, and the other will have an IP in the 192.168.0.0/16 block. Configure the TUN/TAP adapter to use `192.168.2.186` (i.e. CI puppet-master) as its DNS server. If this works, then `sp.aerofs.com` should resolve to `192.168.2.23`. Details about our network configuration can be found [here](../references/networks.html).

See also [setup_ci_1](setup_ci_1.html), [setup_ci_2](setup_ci_2.html), [setup_syncdet_vagrant](setup_syncdet_vagrant.html), [setup_syncdet](setup_syncdet.html) (in progress). **TODO: consolidate them.**

## How to install software

On Windows, install 32-bit cygwin at `C:\cygwin\`, and save cygwin's setup.exe on the desktop. Install things through the cygwin installation wizard.

On OSX, use homebrew wherever possible. Make sure your paths are set properly to use homebrew binaries! `brew doctor` should tell you if you've forgotten this step. 

## Software to install

* Through cygwin/homebrew, install: openssh, vim, git, rsync, python2.7, curl, nc
* On cygwin, also install cygrunsrv

Copy and paste the following into your terminal:

    curl https://bitbucket.org/pypa/setuptools/raw/0.7.5/ez_setup.py | python
    curl https://raw.github.com/pypa/pip/master/contrib/get-pip.py | python
    pip install virtualenv protobuf pyyaml requests

## Set up sshd

On cygwin (run as administrator):

    ssh-host-config -y
    cygrunsrv -S sshd

On OSX, navigate to System Preferences > Sharing and enable "Remote Login"

## Set up ssh access

The controller needs passwordless login to all actors. Append the controller's public key (`~/.ssh/id_rsa.pub`) to the actor's authorized keys file (`~/.ssh/authorized_keys`)

After doing this, ssh from the controller to the actor. This accomplishes two things: it (a) ensures that passwordless login is configured properly, and (b) adds the actor's public key to the controller's known_hosts file. 

## Set up DNS

By default, sp.aerofs.com points to our production SP server. This is almost certainly not what you want. It is most likely that you want to run SyncDET tests in one of two environments:

### Local Production

Use syndet-vagrant (source: syncdet-tests/syncdet-vagrant) to generate SyncDET actor VMs. These should work out of box (lol).

Add the following to config.yaml (TODO: confirm with JGray that this step is no longer needed)

    aero_sp_url: https://unified.syncfs.com:4433/sp

### CI

CI actors need access to the VPN. Follow [these instructions](../references/vpn.html) to set up VPN. 

You will have two ethernet adapters in your network settings. One is for TUN/TAP and will likely have in IP in  the 172.19.10.0/24 block, and the other will have an IP in the 192.168.0.0/16 block. Configure the TUN/TAP adapter to use `192.168.2.186` (i.e. CI puppet-master) as its DNS server. If this works, then `sp.aerofs.com` should resolve to `192.168.2.23`. Details about our network configuration can be found [here](../references/networks.html).
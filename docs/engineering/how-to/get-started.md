Get Started With AeroFS
=======================

By now you should have followed the step-by-step onboarding process at
https://github.com/aerofs/welcome and ran the update-env command.

If everything went well, you should have all the necessary software installed and there should be a
clone of our git repository at ~/repos/aerofs.

Now what?

This guide will briefly explain how to build and run our code base.

AeroFS is split in two parts: the appliance, which is the server side of things, and the desktop
client. (There are also the mobile clients but we won't be discussing that here.)

The appliance is a virtual machine running a set of 20-30 Docker containers. Each Docker container
provides a specific service, like authenticating users, serving the website, enforcing the license
restrictions, and so on. Collectively, they form the AeroFS appliance, and that's what our customers
download and run on their infrastructure.

As an AeroFS developer, you will need to run all these Docker containers locally on a virtual
machine on your Mac. As you make changes to the code base, you rebuild and relaunch the affected
containers so that you can test your changes.


## Building and running the AeroFS appliance

You'll need to be on the VPN to complete this step, since it'll pull some packages from an internal
repository.

We have a set of shell commands to facilitate the management of our Docker containers. You can get a
list and a short description of these commands by running:

     dk-help

Your first step will be to create the virtual machine where the Docker containers will run:

     dk-create-vm

Once `dk-create-vm` completes, type the following command:

     dk-create

This will build and launch all our Docker containers. It takes a while (expect at least 45 mins).
Grab a coffee from Philz, look at other docs, or chat with your new teammates while it's ongoing.
Especially, see `docker/README.md`.

Once `dk-create` completes, your new appliance should be ready. Running `docker ps` should give you
a list of the containers running there. Your appliance is accessible on 192.168.99.100, and to make
things easier we have configured the DNS entry of share.syncfs.com to resolve to this IP address.

So go ahead, open your browser, and navigate to
[https://share.syncfs.com](https://share.syncfs.com). Follow the steps there. If you want to play
with the admin settings of the appliance, you'll need the license file which you'll find at
`~/repos/aerofs/tools/test.license`.

Now you have a fully functional AeroFS appliance.


## Building and running the AeroFS client

This step requires a that you have a running AeroFS appliance. In addition, you need to be on the
VPN to complete this step, since it'll pull some packages from an internal repository.

    cd $HOME/repos/aerofs/
    ./invoke clean proto
    gradle clean dist
    ./invoke --product CLIENT setupenv
    approot/run ~/rtroot/user1 gui

Running gradle will compile the Java source code and create the class files needed to run the
client. Running invoke will create a directory called approot and populate it with all environment-
dependent resources.

Replace `gui` with `cli` to launch AeroFS in the command line. Use `daemon` to run the barebone
daemon process with no UI support.

Run `sh` to enter interactive AeroFS shell. This command requires a running daemon.


### To avoid relaunching the daemon every time

If you work on UI code, it can be anonying that the daemon restarts every time you launch GUI or
CLI: restarting the daemon is slow and also causes your production daemon process to restart. To
work around it:

     touch ~/rtroot/user1/nodm
     approot/run ~/rtroot/user1 daemon &

`nodm` means "no daemon monitor." It asks the UI not to take ownership of the daemon process. The
next time the UI launches it will not restart the daemon.

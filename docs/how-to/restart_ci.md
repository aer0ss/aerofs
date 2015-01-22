See also [How to set up CI](setup_ci.html).

Good morning! So, TeamCity is broken again, now what do we do? These are some notes to help you live after you reboot the CI machine.

## Setup VirtualBox driver

    sudo /etc/init.d/vboxdrv setup

## Start TeamCity

Here is the command you want. It must be run as the aerofsbuild user:

TEAMCITY_SERVER_MEM_OPTS="-Xmx4g -XX:MaxPermSize=270m" /usr/local/TeamCity/bin/runAll.sh start

## Start actors

Log in to https://newci.arrowfs.org:8543 or https://newci.local:8543 and run the "Actor Setup" target under "Regenerate Actors".

## Starting Vagrant agents (skip this if you've done the "Start actors" step above.)

To start Windows agents - there are Three of them - :

cd repos/win7-syncdet-vagrant
WCLIENT_COUNT=3 BRIDGE_IFACE=br0 vagrant up

## Clearing out the actors database

Ummm...I forget
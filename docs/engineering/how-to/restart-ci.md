See also [How to set up CI](setup_ci.html).

Good morning! So, TeamCity is broken again, now what do we do? These are some notes to help you live after you reboot the CI machine.

## Setup VirtualBox driver

    sudo /etc/init.d/vboxdrv setup

## Start TeamCity

Here is the command you want. It must be run as the aerofsbuild user:

TEAMCITY_SERVER_MEM_OPTS="-Xmx4g -XX:MaxPermSize=270m" /usr/local/TeamCity/bin/runAll.sh start

## Start actors

Log in to https://ci.arrowfs.org and run the "Actor Setup" target under "Regenerate Actors".

## Prevent console from blanking and run build catalyst

Log in to a terminal console as user `aerofsbuild/temp123`. The `setterm -blank 0` line
in .bash_profile will block the terminal from going blank, useful to debug kernal paniks.

After logging in, run `~/catalyze-docker-preload.sh` as a temporary workaround for the slow
Ship Enterprise preloading process. The code of the script is is at
[here](https://gist.github.com/weihanwang/152b2607cadd906ad593).

## Clearing out the actors database

Ummm...I forget

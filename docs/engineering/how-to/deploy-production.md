# Introduction

Deploying a new AeroFS system involves several steps.

## Prerequisites

* fwknop (for reaching legacy portknocked servers)

```
brew install fwknop
# we also need kssh and kscp on your $PATH
sudo ln -s /usr/local/bin/kssh $HOME/repos/aerofs/tools/kssh
sudo ln -s /usr/local/bin/kscp $HOME/repos/aerofs/tools/kscp
```

* Fakeroot, dpkg (for building server packages)

```
brew install dpkg fakeroot
```

* s3cmd (more recent version needed for `--cf-invalidate` option)

```
brew install --devel s3cmd
```

* mono (needed for signcode, for signing Windows executables)

```
open AeroFS/Air Computing Team/MonoFramework-MDK-2.10.11.macos10.xamarin.x86.dmg
# and run the package installer
```

* pip (for installing python modules)

```
sudo easy_install pip
```

* protobuf, pyyaml, requests (SyncDET dependencies)

```
sudo pip install protobuf pyyaml requests
```

* SyncDET located at `$HOME/repos/syncdet`

```
mkdir -p $HOME/repos
git clone git@github.arrowfs.org:aerofs/syncdet
```

* (production only) TrueCrypt - install from http://www.truecrypt.org/

# Deployment process

A commit must pass CI before we will push it to production.

```
# build all server code
# build all server packages
# upload all server packages to apt repo
# use puppet to upgrade all packages
ant -Dmode=PROD deploy
# Make sure that all website assets are compiled.
# This step should be unnecessary if you are using watchman and make watch. 
# See "Compiling Less and JS" in src/web/web/README.txt
$ cd src/web/web/
$ make clean && make
# refresh website assets hosted on cloudfront
ant -Dmode=PROD update_cloudfront
# build clients
# upload client zip
# build os-specific installers
# test clients upgrade path (safetynet)
# push new client packages to S3/cloudfront
ant -Dmode=PROD deploy_clients
```

# Deploy Servers

To deploy a server, the process is as follows:

1. Build a debian package
2. Upload the debian package to apt.aerofs.com
3. Run puppet on the instance via one of the following:
    1. ./tools/puppet/kick puppet.arrowfs.org &lt;server hostname&gt;
    2. SSH to the instance and run sudo puppet agent -t

Steps 1 and 2 are not fully standardized, and may be different for each server. Step 3 is standard. 3.1 is a fairly opaque process and takes a minute or two. 3.2 takes just as long but you get much more feedback as it goes.

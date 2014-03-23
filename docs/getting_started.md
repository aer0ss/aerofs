Welcome, new AeroFS teammate!  By now, you have received:

  * a laptop
  * an @aerofs.com email account provided by Google
  * a matching account for github.arrowfs.org

You'll need to do several things to get started with AeroFS:

## Set up full-disk encryption with FileVault

System Preferences -> Security and Privacy -> FileVault -> Turn On FileVault.

This will require that you reboot.

## Create an SSH key, and get it deployed on our machines

```
ssh-keygen -t rsa -b 2048
```

Your private key is `$HOME/.ssh/id_rsa`.  Keep it secret.  Keep it safe.

Your public key is `$HOME/.ssh/id_rsa.pub`.  You'll need to put it in three places:
  * Provide the contents of this file to Drew or Matt or Yuri for further account provisioning
  * [Add it to your account on Github](https://github.arrowfs.org/settings/ssh).

## Register for Gerrit, our code review tool

Register for an account at `gerrit.arrowfs.org`, choosing a username and adding your SSH key. When you've finished registering ask Weihan or Yuri or Drew to add you to the 'Developers' group in Gerrit.

## Install AeroFS and join the Air Computing Team shared folder.

  * Ask Weihan, Linda, or Yuri to invite you to the AeroFS organization and to the `Air Computing Team` shared folder.
  * Accept the invitation via the email you received
  * Log in to https://www.aerofs.com/, then, [download and install the client](https://www.aerofs.com/download). 
  * Accept the shared folder invitation from [here](https://www.aerofs.com/accept).

## Install XCode from the App Store.

Launch App Store (command-space App Store should find it).  Create an account if you didn't when you set up the computer, and search for XCode.  Install it.  This will take a long time and download a lot.

### Install Command Line Tools in XCode.

Launch Xcode. Navigate to Xcode -> Preferences -> Downloads. Click the `Install` button next to Command Line Tools.

## Install a Java IDE (we suggest IntelliJ IDEA CE)

[IntelliJ IDEA (pick the CE) link](http://www.jetbrains.com/idea/download/)

Alternately, if you prefer Eclipse, you can do that.  Oh yeah.  Sure.  Have fun with that.

## Install the Mono platform

Download and install the latest Mono MDK from http://www.go-mono.com/mono-downloads/download.html

(we use Mono for signcode, to sign our Windows executables)

## Install the Android SDK

1. [Download the SDK tools](http://developer.android.com/sdk/index.html) (you probably want the "Use an Existing IDE" tools)
2. Expand the zip into `/usr/local/android`
3. Run the SDK Manager at `/usr/local/android/tools/android`
4. Check at least "Tools" and "Android 4.0.3 (API 15)/SDK Platform".  We recommend “Extras/Intel Hardware Accelerated Execution Manager or Extras/Intel x86 Emulator Accelerator (HAXM)” as well.
5. Click "Install packages..."

## Install Homebrew

[Homebrew](http://mxcl.github.io/homebrew/) is a package manager for OSX.  We use many open-source softwares packaged with homebrew, and have a few of our own formulae for internal things (we'll get to them later)

Paste the following in a terminal:

```
ruby -e "$(curl -fsSL https://raw.github.com/mxcl/homebrew/go/install)"
```

It'll prompt you before it does anything, but the defaults are sane.

## Install some packages with homebrew

Note: Your path may not have `/usr/local/bin` and `/usr/local/sbin` before `/usr/bin` and `/usr/local/sbin`, which causes you to use system binaries instead of those installed by brew if both are available. Running `brew doctor` will tell you if this is the case. If you have this problem you can remedy it by prepending `/usr/local/bin:/usr/local/sbin` to your path.

### Required packages:

Pasteable:

```
brew update && brew upgrade && brew install git python fakeroot ant wget gradle groovy swig qt apt-cacher-ng qemu ruby gpgme dpkg && gem install kramdown jekyll && brew install --devel s3cmd && brew install $HOME/repos/aerofs/tools/{scons,swtoolkit,makensis}.rb $HOME/repos/aerofs/tools/protobuf.brew/protobuf.rb && brew install --HEAD $HOME/repos/aerofs/tools/protobuf.brew/protobuf-objc.rb && ln -s $(brew --prefix apt-cacher-ng)/homebrew.mxcl.apt-cacher-ng.plist ~/Library/LaunchAgents/ && launchctl load ~/Library/LaunchAgents/homebrew.mxcl.apt-cacher-ng.plist
```

  * `git` is used for source control.
  * `python` is used by some services and [SyncDET](https://github.arrowfs.org/aerofs/syncdet), our distributed test harness.  We suggest installing the version from homebrew since it integrates nicely, is reasonably contained, is up-to-date, and keeps you from having to do privilege escalation all the time.
  * `dpkg`, `fakeroot`, `wget` are used to build Debian packages for our servers.  We need a patched version of dpkg, since homebrew and perl modules don't really play well together.
  * `qemu` (which provides `qemu-img`) is used to convert disk images to different formats.
  * `gradle` is used to build some Java projects.
  * `swig` is used for our native libraries
  * `s3cmd` (developer version required) is used for pushing assets and installers to S3 buckets and cloudfront.  Only the `--devel` version of `s3cmd` supports `--cf-invalidate`
  * `qt` provides `qmake` which generates Makefiles for many of our native libraries
  * `scons` and `swtoolkit` are used to build libjingle
  * `apt-cacher-ng` is used to speed up local prod VM builds.
  * `ruby` and `kramdown` are used by tools/markdown_watch.sh to compile .md files into .html
  * `makensis` is used to build Windows installers
  * `gpgme` is a library wrapping gpg, which we use for license file generation/verification
  * `jekyll` is used to build the API docs, which are part of the web package

### Optional packages:

```
brew install bash-completion rdesktop coreutils autoconf automake
```

## Install some applications needed for local prod

* `vagrant` may be found [here](http://www.vagrantup.com)
* `VirtualBox` may be found [here](http://www.virtualbox.org/wiki/Downloads) (Recommended version: 4.2.16).

## Obtain the AeroFS source code

Before this step, you'll need your accounts created on gerrit and github, so get to that.

And make sure you put the repo at `$HOME/repos/aerofs`:

```
mkdir -p $HOME/repos && cd $HOME/repos
git clone ssh://<gerrit username>@gerrit.arrowfs.org:29418/syncdet
git clone ssh://<gerrit username>@gerrit.arrowfs.org:29418/aerofs
```

### Install git-review for a better gerrit experience

```
cd $HOME/repos/aerofs
pip install git-review
scp -p -P 29418 <gerrit username>@gerrit.arrowfs.org:hooks/commit-msg .git/hooks/
git remote add gerrit ssh://<gerrit username>@gerrit.arrowfs.org:29418/aerofs
```

This installs `git-review`, installs a post-commit hook for gerrit and adds the gerrit remote.

## Set up MySQL and Redis for unit tests

`brew install mysql redis`. Follow the instructions from `brew info redis` and `brew info mysql` to setting them to run automatically via `launchctl`.

Set a good root password for MySQL, then create the test user for MySQL:

```
mysql -u root -p << EOF
CREATE USER 'test'@'localhost' IDENTIFIED BY PASSWORD '*7F7520FA4303867EDD3C94D78C89F789BE25C4EA' ;
GRANT ALL PRIVILEGES ON *.* TO 'test'@'localhost';
FLUSH PRIVILEGES;
EOF
```

## Set up ejabberd for unit tests

`brew install ejabberd`. Copy the ejabberd config, key/cert and authorization scripts as listed below.

```
cp repos/aerofs/tools/ejabberd/ejabberd.* /usr/local/etc/ejabberd/
cp repos/aerofs/tools/ejabberd/ejabberd_auth_all /usr/local/bin/
```

Then, start ejabberd using `/usr/local/sbin/ejabberdctl start`.

## Install some python modules

If you get a permissions error, make sure you've installed Python with homebrew and that you're using the right Python (`which python` should give `/usr/local/bin/python`, not `/usr/bin/python`).  You shouldn't have to `sudo` this to make it work.

```
pip install protobuf requests PyYAML
```

## Install Tunnelblick and get on the AeroFS VPN

Ping Drew or Matt to provision you a VPN config bundle.

See [VPN](https://github.arrowfs.org/aerofs/aerofs/wiki/VPN), and in particular, follow the bit about setting up a new engineer/box.

## Build and launch private AeroFS Service VM (aka local prod)

This VM is required to run your private AeroFS clients that are isolated from the production and other developers' environments.

You'll need to be on the VPN to complete this step, since it'll pull some packages from an internal repository.

```
echo source ~/repos/aerofs/tools/bashrc/include.sh >> ~/.profile
sudo sh -c 'echo 192.168.51.150 unified.syncfs.com >> /etc/hosts'
source ~/repos/aerofs/tools/bashrc/include.sh
lp-create
```

The last step may take a while (expect 30 mins). Grab a coffee from Philz, look at other docs, or chat with your new teammates while it's ongoing. Once done, you can run `lp-ssh` to log in your VM. See [Local Production (aka. local prod)](https://github.arrowfs.org/aerofs/aerofs/wiki/Local-Production-%28aka.-local-prod%29) for more information about the private environment.

## Build and launch the client

You'll need to be on the VPN to complete this step, since it'll pull some packages from an internal repository.

```
cd $HOME/repos/aerofs/ && ant -Dmode=PRIVATE -Dproduct=CLIENT clean setupenv build_client
mkdir -p $HOME/repos/aerofs/user_data/
cd approot/ && ./run ../user_data/rtroot gui
```

The `-Dmode=PRIVATE` flag points the client to your private environment. Specify `-Dmode=PUBLIC` to build clients for the public production environment (discouraged).

## Signing Up

For easy signup you can use signup tool:

```
~/repos/aerofs/tools/signup.sh -u TEST_USER_NAME@aerofs.com
```

When asked for vagrant password use: `vagrant`. It will create user signup record with default password `uiuiui`. You can specify -p flag to set custom password as TEST_USER_NAME you can use: YOUR_USER_NAME+ANY_SUFFIX@aerofs.com e.g. bob+test@aerofs.com.
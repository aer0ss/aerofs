## Welcome, new AeroFS teammate!

By now, you have received:

  * a laptop
  * an @aerofs.com email account provided by Google

You'll need to do several things to get started with AeroFS.

## Make sure your local user ID is good

Your user ID should follow the format "<first_name><last_name_initial>" e.g. "alext." Otherwise there will be ongoing pain in the future. Creating a new account and deleting the existing one is one of easier ways to change your user ID.

## Set up full-disk encryption with FileVault

System Preferences -> Security and Privacy -> FileVault -> Turn On FileVault.

This will require that you reboot.

## Create an SSH key, and get it deployed on our machines

    $ ssh-keygen -t rsa -b 2048

Your private key is `$HOME/.ssh/id_rsa`.  Keep it secret.  Keep it safe.

Your public key is `$HOME/.ssh/id_rsa.pub`. Provide this file to [Drew](drew@aerofs.ccom) or [Matt](matt@aerofs.com) for further account provisioning.

## Register for Gerrit, our code review tool

Log into `gerrit.arrowfs.org` using your @aerofs.com Google account. Choose a username and add your SSH key. When you've finished registering ask Weihan or Drew to add you to the 'Developers' group in Gerrit.

## Register for JIRA, our issue tracker

Log into [Atlassian JIRA](https://aerofs.atlassian.net/) using your @aerofs.com credentials and use the interface to ask for access.  They will quickly be granted by someone with mighty admin powers.

## Install AeroFS Canary and join the team folder

  * Once your AeroFS is installed, go to tray menu > Preferences... > Advanced..., and enable the Canary mode.
  * Ask Weihan or Yuri to invite you to the AeroFS organization and to the `Air Computing Team` shared folder.
  * Accept the invitation via the email you received
  * [Download and install the AeroFS client](https://www.aerofs.com/download). 
  * [Accept the shared folder invitation](https://www.aerofs.com/accept).

## Install XCode

[XCode](https://itunes.apple.com/us/app/xcode/id497799835?mt=12) is a huge (~2.5 GB) application.  Fortunately you can continue to work on this guide while it downloads.  Just be sure it's finished installing before you run the `brew` commands.

## Install XCode command line tools

    $ gcc
    
will launch a dialog prompting you to install XCode command line tools. Follow the instructions to install the tools.

## Install JDK 8

[JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

[JCE Unlimited Strength Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html)

You should configure your environment to pick up JDK8 instead of the default JDK6 shipped with OSX.
For bash this can be achieved by adding the following line to your `~/.bash_profile`:

    export JAVA_HOME="$(/usr/libexec/java_home -v '1.8*')"

## Install Homebrew

[Homebrew](http://mxcl.github.io/homebrew/) is a package manager for OSX.  We use many open-source softwares packaged with homebrew, and have a few of our own formulae for internal things (we'll get to them later)

Paste the following in a terminal:

    ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"

It'll prompt you before it does anything, but the defaults are sane.

## Install packages

Note: Do not use `sudo` for the following command. You should not need it if the paths are set up properly in the previous step.

    brew update && brew upgrade && brew install git python fakeroot ant wget maven gradle groovy swig qt apt-cacher-ng qemu pigz ruby gpgme dpkg npm s3cmd bash-completion coreutils autoconf automake

    brew install $HOME/repos/aerofs/tools/{scons,swtoolkit,makensis}.rb $HOME/repos/aerofs/tools/protobuf.brew/protobuf.rb && brew install --HEAD $HOME/repos/aerofs/tools/protobuf.brew/protobuf-objc.rb
    
    gem install kramdown jekyll
    
    ln -s $(brew --prefix apt-cacher-ng)/homebrew.mxcl.apt-cacher-ng.plist ~/Library/LaunchAgents/ && launchctl load ~/Library/LaunchAgents/homebrew.mxcl.apt-cacher-ng.plist

    pip install virtualenv protobuf requests pyyaml

    npm install -g less minifier uglify-js

This step takes a while. It's probably a good time to look around in our [mailing list](../references/mailing_list.html).

  * `git` is used for source control.
  * `python` is used by some services and SyncDET (source: the syncdet repo on gerrit), our distributed test harness.  We suggest installing the version from homebrew since it integrates nicely, is reasonably contained, is up-to-date, and keeps you from having to do privilege escalation all the time.
  * `dpkg`, `fakeroot`, `wget` are used to build Debian packages for our servers.  We need a patched version of dpkg, since homebrew and perl modules don't really play well together.
  * `qemu` (which provides `qemu-img`) is used to convert disk images to different formats.
  * `pigz` is a parallel gzip implementation used to speed up compressing disk images
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
  * `pyyaml` is used for SyncDET to parse yaml files.
  * `npm` is used to run JavaScript unit tests.
  * `less` is used to compile less scripts into CSS.
  * `minifier` is used to minify JavaScripts.
  * `uglify-js` is used to beautify JavaScripts.

## Update your command path

Prepend (not append):

    /usr/local/bin:/usr/local/sbin:/usr/local/Cellar/ruby/{version}/bin

to your path, with `{version}` replaced by the version of your ruby install.

## Install IDEs

We suggest [IntelliJ IDEA CE](http://www.jetbrains.com/idea/download/) for Java and [PyCharm](http://www.jetbrains.com/pycharm/download/) for Python.

To compile from IntelliJ you may need to force it to run under Java 8. To do that:
in /Application/IntelliJ IDEA*.app/Contents/Info.plist
change `JVMVersion` to `1.8*`

You may also need to manually point IntelliJ to the newly installed JDK:

File > Project Structure... > SDKs > + > JDK
the path should be something like /Library/Java/JavaVirtualMachines/jdk1.8.0_11.jdk/Contents/Home

You may also need to manually point IntelliK to the newly installed gradle:

Preferences > Build, Execution, and Deployment > Built Tools > Gradle > Use local gradle distribution
the path should be something like /usr/local/opt/gradle/libexec"

Import the the "aerofs" and "baseline" repos into IntelliJ. Because IntelliJ has trouble parsing the gradle files completely it is necessary to manually enable some of the libraries we use:

File > Project Structure > Project Settings > Modules > Dependencies
Enable the checkboxes for all dependencies in the "src_base" and "src_bifrost" modules.

We require that you use either an IDE or a language validator to check syntax and formatting for code in markup and scripting languages, such as Python, HTML, CSS, and JavaScript. Note that the first time you open IntelliJ, IntelliJ will launch background tasks to download various libraries from Maven Central to the local Maven cache. This process is time-intensive (~15 minutes) and you will see background tasks running, one after another, in IntelliJ.

## Install VM tools

* `vagrant` may be found [here](http://www.vagrantup.com)
* `VirtualBox` may be found [here](http://www.virtualbox.org/wiki/Downloads) (Required version: 4.2.16).

## Install Mono MDK

Download and install the latest [Mono MDK](http://www.go-mono.com/mono-downloads/download.html). (for signcode, to sign our Windows executables. Not required if you don't deploy production releases.)

## Obtain the AeroFS source code

Before this step, you'll need your accounts created on gerrit, so get to that.

Make sure you put the repo at `$HOME/repos/aerofs`:

    mkdir -p $HOME/repos && cd $HOME/repos
    git clone ssh://<gerrit username>@gerrit.arrowfs.org:29418/syncdet
    git clone ssh://<gerrit username>@gerrit.arrowfs.org:29418/aerofs

### Install git-review for a better gerrit experience

    cd $HOME/repos/aerofs
    pip install git-review
    scp -p -P 29418 <gerrit username>@gerrit.arrowfs.org:hooks/commit-msg .git/hooks/
    git remote add gerrit ssh://<gerrit username>@gerrit.arrowfs.org:29418/aerofs

This installs `git-review`, installs a post-commit hook for gerrit and adds the gerrit remote.

### Enable vim syntax highlighting

    $ cat > ~/.vimrc <<END
    syntax on
    set hlsearch
    END

## Set up MySQL and Redis for unit tests

`brew install mysql redis`. Follow the instructions from `brew info redis` and `brew info mysql` to setting them to run automatically via `launchctl`.

Set a good root password for MySQL, then create the test user for MySQL:

    mysql -u root -p << EOF
    CREATE USER 'test'@'localhost' IDENTIFIED BY PASSWORD '*7F7520FA4303867EDD3C94D78C89F789BE25C4EA' ;
    GRANT ALL PRIVILEGES ON *.* TO 'test'@'localhost';
    FLUSH PRIVILEGES;
    EOF

## Set up ejabberd for unit tests

`brew install ejabberd`. Copy the ejabberd config, key/cert and authorization scripts as listed below.

    cp $HOME/repos/aerofs/tools/ejabberd/ejabberd.* /usr/local/etc/ejabberd/
    cp $HOME/repos/aerofs/tools/ejabberd/ejabberd_auth_all /usr/local/bin/

Then, start ejabberd using `/usr/local/sbin/ejabberdctl start`.

## Install Tunnelblick and get on the AeroFS VPN

Ping Drew or Matt to provision you a VPN config bundle.

See [VPN](../references/vpn.html), and in particular, follow the bit about setting up a new engineer/box.

## Build and launch private AeroFS Service VM (aka local prod)

This VM is required to run your private AeroFS clients that are isolated from the production and other developers' environments.

You'll need to be on the VPN to complete this step, since it'll pull some packages from an internal repository.

     echo source ~/repos/aerofs/tools/bashrc/include.sh >> ~/.profile
     source ~/repos/aerofs/tools/bashrc/include.sh
     ssh apt.aerofs.com # (create password and don't forget it!)
     lp-create

The last step may take a while (expect 30 mins). Grab a coffee from Philz, look at other docs, or chat with your new teammates while it's ongoing. Once done, you can run `lp-ssh` to log in your VM. See [Local Production (aka. local prod)](../references/local_prod.html) for more information about the private environment.

## Build and launch the client

This step requires a running local prod. In addition, you need to be on the VPN to complete this step, since it'll pull some packages from an internal repository. In addition, 

    cd $HOME/repos/aerofs/
    gradle clean dist
    ./invoke --mode PRIVATE --product CLIENT setupenv
    approot/run ~/rtroot/user1 gui

Running gradle will compile the Java source code and create the class files needed to run the client. Running invoke will create a directory called approot and populate it with all environment-dependent resources.

The `--mode PRIVATE` flag points the client to your private environment. Specify `--mode PUBLIC` to build clients for the public production environment (discouraged).

Replace `gui` with `cli` to launch AeroFS in the command line. Use `daemon` to run the barebone daemon process with no UI support.

Run `sh` to enter interactive AeroFS shell. This command requires a running daemon.

### To avoid relaunching the daemon every time

If you work on UI code, it can be anonying that the daemon restarts every time you launch GUI or CLI: restarting the daemon is slow and also causes your production daemon process to restart. To work around it:

     touch ~/rtroot/user1/nodm
     approot/run ~/rtroot/user1 daemon &
     
`nodm` means "no daemon monitor." It asks the UI not to take ownership of the daemon process. The next time the UI launches it will not restart the daemon.

## Serve web files properly

By default, the VM uses its own copy of all the web files. This means that you can't see any changes to the website without restarting the entire web service. To fix this:

    lp-ssh
    cd /opt/web
    sudo mv web web.back
    sudo ln -s /mnt/aerofs/src/web/web web

This replaces the VM's web folder with a symlink to the web folder in your repo. Now you can see e.g. CSS changes automatically appear, without having to restart anything.

## Compile static files

Compiled files are not included in source control, so to get all the styles and JS working on the local version of the website you'll need to compile them. For how to do so, please see "Compiling Less and JS" in src/web/web/README.txt. 

You'll need to install some dependencies, then run `make watch` and hopefully never need to pay attention to Less/JS compilation/minification again!

## Sign up accounts in local prod

Go to the URL `https://share.syncfs.com` to create the first admin account.

Or, you can use the signup script:

    ~/repos/aerofs/tools/signup.sh -u TEST_USER_NAME@aerofs.com

When asked for vagrant password use: `vagrant`. It will create user signup record with default password `uiuiui`. You can specify -p flag to set custom password as TEST_USER_NAME you can use: YOUR_USER_NAME+ANY_SUFFIX@aerofs.com e.g. bob+test@aerofs.com.

## Set up and run SyncDET tests

Skim through the SyncDET manual at docs/usermanual.pdf in the syncdet repo to get familiar with SyncDET concepts.

Before running SyncDET tests, you need to [setup SyncDET actors VMs](setup_syncdet_actors.html). Then:

Build client packages for all OSes:

    $ cd ~/repos/aerofs
    $ invoke --product=CLIENT --mode=PRIVATE  --unsigned setupenv clean proto build_client package_clients
    $ invoke prepare_syncdet --product=CLIENT

Replace CLIENT WITH TEAM_SERVER if you are testing the team server.

Clean up and install the client packages to the actors:

    $ invoke syncdet --syncdet-case=lib.cases.clean_install

You only need to run the above two steps once until you need to update client binaries.

Run a single test case:

    $ invoke syncdet --syncdet-case=core.basic.should_rename_file
    
This test case correponds to the Python file ~/repos/aerofs/system-tests/syncdet/core/basic/should_move_file.py.
     
Run a scenario that contains all basic test cases:

    $ invoke syncdet --syncdet-scenario=./system-tests/syncdet/core/basic/test_basic.scn
    
Now, run all the tests!

    $ invoke syncdet --syncdet-scenario=./system-tests/syncdet/all.scn # all tests

For convenience, the `test_system` ant target runs clean_install before other tests:

    $ ant test_system -Dcase=core.basic.should_move_file


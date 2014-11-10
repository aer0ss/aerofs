## Background

So, you want to build JDK8 on Linux.

You probably want it to still work on Ubuntu 10.04.  How cute.

JDK8 has a build-time dependency of JDK7 (at least jdk7u7, according to docs).

JDK7 for Ubuntu 10.04 does not exist in any official repo.  There exist some third-party ones, but we don't trust them.
JDK7 for Ubuntu 12.04 depends on many libraries that are newer than those versions in 10.04.  Sigh.

## Preparation

Simplest workaround appears to be for us to manually download the Oracle
official JDK7 packages and use those to bootstrap JDK8.

http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html

Java SE 7u71 was the latest JDK7 at the time this was written.
Download the Linux x86 and Linux x64 .tar.gz packages and place them in a folder shared with the buildboxes:

    mv jdk-7u71-linux-i586.tar.gz ~/repos/aerofs/tools/vagrant/build32
    mv jdk-7u71-linux-x64.tar.gz ~/repos/aerofs/tools/vagrant/build64

## Building

From within the VM:

  - Extract JDK7 packages into homedir and add to PATH:

        tar xzvf /vagrant/jdk-*.tar.gz
        JDKPATH=$(ls -d $HOME/jdk*)/bin
        export PATH=$JDKPATH:$PATH

  - Install a bunch of build dependencies:

        sudo apt-get build-dep openjdk-6-jdk
        sudo apt-get install mercurial

  - Fetch the JDK8 source and check out a known-good revision

        hg clone --pull http://hg.openjdk.java.net/jdk8u/jdk8u openjdk8u
        cd openjdk8u
        bash get_source.sh
        bash ./make/scripts/hgforest.sh update -c jdk8u40-b13

 - Apply patch that remove the AI\_CANONNAME hint from calls to getaddrinfo
   to avoid unnecessary (and slow) reverse DNS lookup with older versions
   of glib as described in a [bug report](https://sourceware.org/bugzilla/show_bug.cgi?id=15218)

  - Configure and build JDK8

        bash ./configure
        make JOBS=4 all

Congratulations, you now have a full j2sdk-image somewhere under your build/ folder.
Now we need to trim it down.

## Reducing package size

From within the VM:

  - Strip all the executables/libraries to reduce their size

        cd build/
        # cd all the way down to the j2sdk-image folder, then:
        find . | grep '\.so$' | xargs strip
        find . | grep '\.diz' | xargs rm
        strip jre/bin/*

  - Trim the jdk image using our jdk-trim scripts:

        git clone https://github.com/aerofs/openjdk-trim.git
        cd openjdk-trim
        ./jdk-trim linux ../wherever/j2sdk-image j2sdk-trim-linux

  - Move trimmed JRE to AeroFS main repo (adjust architecture as appropriate):

        rm -rf ~/repos/aerofs/resource/client/linux/amd64/jre/
        mv j2sdk-trim-linux/jre/ ~/repos/aerofs/resource/client/linux/amd64/jre

  - Restore TLS-related things:

        cd ~/repos/aerofs
        git checkout -f resource/client/linux/amd64/jre/lib/security/cacerts
        git checkout -f resource/client/linux/amd64/jre/lib/security/local_policy.jar
        git checkout -f resource/client/linux/amd64/jre/lib/security/US_export_policy.jar

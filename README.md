AeroFS
---

This repository holds the source code for
 - AeroFS Private Cloud appliance
 - AeroFS desktop client (Linux, macOS, Windows)


This is an ongoing restoration project: the last maintenance release of
this code dates back to early 2018, and many of the necessary tools have
since evolved in incompatible ways, or gone unmaintained. In addition,
the build process relied on some external dev environment setup that was
not well standardized, and some internal infrastructure (VPN, AWS SES,
internal maven repo, internal docker registry, internal apt repo, ...)
that is long gone and/or impractical to reproduce in the context of an
Open Source project.

This repo will (hopefully) eventually be readily buildable and might even
at some point provide ready-to-install artifacts, but we're not there yet.

## Build requirements

macOS is currently the best-supported build host

 - git
 - bash
 - JDK 8 (newer versions have not been tested and will probably break)
 - Python 3
 - Go 1.7+
 - [colima](https://github.com/abiosoft/colima)
 - docker client
 - protobuf 2.6.1 (newer versions will *NOT* work)
 - Qt libraries (only needed for qmake build tool)
 - xcode developer tools

and more. see (outdated) [playbook.yaml](tools/env/playbook.yaml)

## Getting started

Add the following to your `~/.zshrc` / `~/.bashrc`

```bash
source ~/repos/aerofs/tools/bashrc/include.sh
```

Create a colima machine (named `docker-dev` by default),
to build and deploy relevant containers:

```bash
dk-create-vm
```

Then to build everything (desktop client and server containers),
and spin up a set of containers on your newly created colima
machine:

```bash
dk-create
```

It'll take a while, so be patient, maybe peruse some of the old
documentation or the code if you're curious. If everything goes
well you'll eventually get to:

```shell

>>> PASSED. Screenshots at (...)/out.shell/screenshots/bunker/setup

Services is up and running. You may create the first user with:

   open http://share.syncfs.com
```

When asked for a license file to log into the admin interface, you
can use [system-tests/webdriver-lib/root/test.license](system-tests/webdriver-lib/root/test.license)

## Known Issues

Lots! And probably more to be found as the archaeological dig continues!
This list will be updated as issues are fixed

 - protobuf 2.6.1 needs to be installed from source right now
   - TODO: either bump to newer protobuf, or automate installation
 - dependency on qmake is annoying
   - TODO: switch to CMake or meson/ninja ?
 - Lots of python 2.7 code, which will eventually need to be port to py3
 - recent versions of fakeroot hang on recent macOS, breaking deb pkg build
 - *.syncfs.com cert has expired and will need to be replaced
 - devpi pypi cache works fine for pip but not for easy_install, which breaks
   weirdly in some cases. This should eventually be resolved by upgrading to
   py3 and a modern packaging toolchain
 - base docker images are *OLD* and probably full of CVEs
 - release appliance builds rely on CoreOS which is long discontinued
   - consider switching to [flatcar](https://www.flatcar.org/)
   - explore alternate k8s-based deployment model
 - Apple Silicon is not supported
 - Windows is still stuck to 32bit builds
 - We don't have codesigning creds for windows or mac
 - We don't have a CI setup
 - web apps depend on way outdated node/npm and will need careful lifting
   to more modern toolchain
 - java build depends on very old gradle 2.14
   - TODO: bump to more recent gradle
 - the dev setup relied on AWS SES to send signup and other emails from the
   server. Those credentials are obviously not usable anymore. Email sent
   from a residential address is unlikely to make it into a mailbox, so
   we'll need to make it easier to provide SMTP creds to keep dev setup working
   Alternatively, we could also route all outgoing email to a container holding
   a simple webmail interface to make them easy to access in one place, even
   when working offline...

updater
=======


Why?
----

To address a number of issues in the current (re)packaging flow:

  - building packages on dev machines is slow: long edit/test cycle for syncdet

  - repackaging on the appliance is slow: ~2min wait on every appliance install/upgrade

  - the repackaging container is massive
      - slow build
      - slow push to/pull from docker repository
      - large disk footprint on docker repository
      - large disk footprint on appliance

  - packages are large and most of their contents are not actually updated across releases
    (e.g. JRE, static assets, java deps, ...)
      - slow download of upgrades
      - high bandwidth consumption on upgrade
      - unnecessary disk writes on upgrade


What
----

A complete overhaul of the (re)packaging flow:

  - a new launcher/updater binary

  - trimmed down packages that only include the launcher/updater

  - a content-addressable file store in the repackaging container

  - a set of JSON manifests that describe the file structure for
    each package {client, ts} x {linux32, linux64, windows, osx}


 => the size of the package data drops by more than 500MB

 => repackaging now processes much smaller files, significantly
    cutting down on execution time

 => the bandwidth usage for updates is significantly reduced

 => the packages only need to be built when the updater changes,
    which should be extremely rare, thereby dramatically reducing
    the edit/test cycle for local syncdet tests


Downsides / limitations
-----------------------

The very first setup might be slightly slower since the first update must
download all the files individually and they won't benefit from whole-package
compression.

golang-builder
==============

Build static golang binaries and package them into docker containers


License
-------

BSD 2-Clause, see accompanying LICENSE file.


Requirements
------------

  bash
  docker 1.5+


Usage
-----

    build.sh <image> <package> [<source> [<mapping> [<Dockerfile>]]]


The default use case is to call the build script from the root directory
of the package being built, with any dependencies vendored in and the
Dockerfile at the root of the package.

The `<source>` can be changed to easily include non-vendored dependencies
into the build context. For instance, given the following hierarchy:


    src/
        acme.com/
            common/
            foo/

Where `foo` is the service to be built and common is a package it depends on.

The following command can be used, from `src/acme.com/foo` :

    build.sh <foo> acme.com/foo ..

This will result in all of `src/acme.com` being used as build context, under
`$GOPATH/src/acme.com`

Similarly, `<mapping>` can be changed from its default value to accommodate
source layouts that deviate from golang's conventions and `<Dockerfile>` can
point to a Dockerfile at a non-default location, including outside of the
build context.


Dependency resolution
---------------------

For ease of use, golang-builder uses `go get` to automatically fetch remote
dependencies from github and other public repositories supported by default.

Relying on this feature should be avoided in favor of vendored dependencies.


Patching standard lib
---------------------

Fully static builds allow easy patching of the standard library. golang-builder
leverages that by automatically applying patches found in the `patches` subdir
of the package being built.

Care should be taken that the patches cleanly apply against the version of go
used in the container (1.4.2 at this time).


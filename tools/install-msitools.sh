#!/bin/bash

#######
# BEWARE: this file had to be duplicated in https://github.com/aerofs/aerofs-infra/tree/master/ci/agents/root/scripts
# If you change this file, please do so with the other one. Or terrible things will happen.
#######

set -eu

if [ $# = 1 ] && [ $1 = cleanup-apt ]; then
    CLEANUP_APT=1
else
    CLEANUP_APT=0
fi

# the only difference between build and runtime dependencies is build
# dependencies are removed after install.
readonly BUILD_DEPENDENCIES="libtool build-essential intltool gcc-4.8 \
    gobject-introspection libglib2.0-dev libgsf-1-dev uuid-dev"
# note that though curl is not required at run time, other services on this
# container requires curl so it will be unpolite for us to uninstall it.
readonly RUNTIME_DEPENDENCIES="curl libglib2.0-0 libgsf-1-114 uuid-runtime"

readonly GCAB_NAME="gcab"
readonly GCAB_VERSION="0.4"
readonly GCAB_DIR="${GCAB_NAME}-${GCAB_VERSION}"

readonly MSITOOLS_NAME="msitools"
readonly MSITOOLS_VERSION="0.93"
readonly MSITOOLS_DIR="${MSITOOLS_NAME}-${MSITOOLS_VERSION}"

function setup() {
    echo ">> Setting up build environment for msitools..."

    apt-get update
    apt-get install -y ${BUILD_DEPENDENCIES} ${RUNTIME_DEPENDENCIES}

    echo ">> Setup complete."
}

function checkout() {
    if [ $# -ne 3 ] ; then
        echo "Usage: $(basename $0) PROJECT VERSION CHECKSUM"
        echo "  PROJECT - the name of the project to checkout."
        echo "  VERSION - the version of the project to checkout."
        echo "  CHECKSUM - the expected sha256sum of the downloaded artifact."
        exit 1
    fi

    local PROJECT="$1"
    local VERSION="$2"
    # expected checksums can be found at:
    # "${GNOME_REPO}/${PROJECT}/${VERSION}/${PROJECT}-${VERSION}.sha256sum"
    local EXPECTED_CHECKSUM="$3"

    local GNOME_REPO="http://ftp.gnome.org/pub/GNOME/sources"
    local ARTIFACT="${PROJECT}-${VERSION}.tar.xz"
    local ARTIFACT_SRC="${GNOME_REPO}/${PROJECT}/${VERSION}/${ARTIFACT}"

    echo ">> Downloading ${PROJECT}..."
    curl "${ARTIFACT_SRC}" -o "${ARTIFACT}"
    test -f "${ARTIFACT}"

    echo -n ">> Verifying ${PROJECT} checksum... "
    local ACTUAL_CHECKSUM="$(sha256sum "${ARTIFACT}" | awk '{print $1}')"

    if [ "${ACTUAL_CHECKSUM}" == "${EXPECTED_CHECKSUM}" ] ; then
        echo "match!"
    else
        echo "mismatch!"
        exit 1
    fi

    echo ">> Extracting ${PROJECT}..."
    tar xf "${ARTIFACT}"
    test -d "${PROJECT}-${VERSION}"
    rm "${ARTIFACT}"

    echo ">> Checkout complete."
}

function build_and_install() {
    if [ $# -ne 2 ] ; then
        echo "Usage: $(basename $0) PROJECT DIR"
        echo "  PROJECT - the name of the project to build."
        echo "  DIR - the directory where project source code is located."
        exit 1
    fi

    local PROJECT="$1"
    local DIR="$2"

    pushd "${DIR}" > /dev/null
    echo ">> Building ${PROJECT}..."
    ./configure

    # the latest upstream gcc at the time of writing is 4.9.
    #
    # gcab and msitools are tested on gcc-4.8. The output artifacts of msitools
    # when built with gcc-4.9 fail the tests in the test suite.
    make CC="/usr/bin/gcc-4.8"

    echo ">> Running ${PROJECT} tests..."
    tests/testsuite

    echo ">> Installing ${PROJECT}..."
    make install
    ldconfig
    popd > /dev/null

    echo ">> Installation complete."
}

function cleanup() {
    echo ">> Cleaning up..."

    rm -rf "${GCAB_DIR}" "${MSITOOLS_DIR}" 

    if [ ${CLEANUP_APT} = 1 ]; then
        apt-get purge -y ${BUILD_DEPENDENCIES}
        rm -rf /var/lib/apt/lists/*
    fi
}

function main() {
    setup

    checkout "${GCAB_NAME}" "${GCAB_VERSION}" \
        "f907b16f1246fbde9397363d9c4ad2291f2a8a53dcd4f5979d3912bb856991b8"
    build_and_install "${GCAB_NAME}" "${GCAB_DIR}"

    checkout "${MSITOOLS_NAME}" "${MSITOOLS_VERSION}" \
        "a2d25f05437047749a068946ed019839b88350928511cc7c021ea390413b9dc5"
    build_and_install "${MSITOOLS_NAME}" "${MSITOOLS_DIR}"

    cleanup
}

main

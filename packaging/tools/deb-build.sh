#!/bin/bash
# N.B. do not set -e this script, it checks its own error codes.

# error codes
readonly ERRBADARGS=1
readonly ERRNODEB=2
readonly ERRNOVERSION=3
readonly ERRFAKEROOTFAILED=4

###############################################################################
#
# AEROFS DEB BUILDER
#
# Instructions:
# - "brew install dpkg" if you don't have dpkg-deb on your system.
# - "brew install $HOME/repos/aerofs/tools/fakeroot.rb" if you lack fakeroot
# - "brew install fakeroot" if the previous command doesn't work
#
###############################################################################

# Check if a builder script exists for the requested package
# Params:
#   None
# Return:
#   0 when the builder script exists
#   2 when the builder script doesn't exist
function check_for_builder_script() {
    test -d $DEBNAME
    if [ $? -ne 0 ]
    then
        echo "Don't know how to build '$DEBNAME'."
        exit $ERRNODEB
    fi
}

# Get the current version number for a given AeroFS debian package.
# Params:
#   $1 = the name of the debian package.
#   $2 = the target repository
# Return:
#   0 when the version number is successfully obtained from the server.
#   1 when the version number does not exist on the server (new package).
#   2 when there was some other error (missing wget?).
# Echo:
#   The current version number.
function get_version_number_from_repository()
{
    wget \
        --output-document $1.current.ver \
        --no-check-certificate -q \
        --no-cache http://apt.aerofs.com/ubuntu/$2/versions/$1.current.ver

    local error_code=$?

    if [ $error_code -eq 0 ]
    then
        cat $1.current.ver
        rm $1.current.ver
        return 0
    elif [ $error_code -eq 8 ]
    then
        # not found (404). likely a new package.
        return 1
    else
        return 2
    fi
}

# Computes the deb package version number for the new package
# Params:
#   None
# Returns:
#   0: Successfully calculated a new, incremented version number for the new package
#   non-zero: Any other error condition
function compute_new_version_number() {
    VERSION=$(get_version_number_from_repository aerofs-$DEBNAME $TARGET_REPOSITORY)
    local error_code=$?

    if [ $error_code -eq 1 ]
    then
        # Default version for new packages.
        VERSION=1.0.0
    elif [ $error_code -eq 2 ]
    then
        echo "Failed to get current version."
        exit $ERRNOVERSION
    fi

    local major=$(echo $VERSION | awk -F'.' '{print $1}')
    local minor=$(echo $VERSION | awk -F'.' '{print $2}')
    local build=$(echo $VERSION | awk -F'.' '{print $3}')
    build=$[$build+1]
    readonly NEW_VERSION=$major.$minor.$build
}

# Creates a debian control file
# Params:
#   None
# Returns:
#   0: Successfully created the control file
#   non-zero: Any other error condition
function create_deb_control_file() {
    echo "Current $DEBNAME version is $VERSION. New version is $NEW_VERSION."
    echo $NEW_VERSION > aerofs-$DEBNAME.current.ver
    echo "Version: $NEW_VERSION" >> $CONTROL_FILE
}

# Creates a debian control file
# Params:
#   None
# Returns:
#   0: Successfully created the debian package
#   4: fakeroot failed to run
#   other: Any other failure condition
function build_deb() {
    local tmpdir=$(mktemp -d "$DEBNAME-XXXXXX")
    # We have to do the copy and chown under the fakeroot environment so the
    # package unpacks to files with root's uid/gid.
    fakeroot << EOF
    set -e
    cp -a $DEBNAME/* $tmpdir/
    chown -R 0:0 ${tmpdir}
    dpkg-deb --build ${tmpdir} aerofs-${DEBNAME}.deb
EOF

    if [ $? -ne 0 ]
    then
        echo "Failure in fakeroot. (Do you have dpkg and fakeroot installed?)."
        exit $ERRFAKEROOTFAILED
    fi

    rm -rf ${tmpdir}

    # Clean up the control file (remove the version number, i.e. the last line).
    cat $CONTROL_FILE | grep -v "$(cat $CONTROL_FILE | tail -1)" > $CONTROL_FILE
}

###############################################################################
#
#
# Main script
#
#
###############################################################################

function print_usage() {
    echo "Usage: $0 <debian_name> <repository>"
    echo " <deb-name>   debian package to build"
    echo " <repository> repository to upload package to (PROD|CI|STAGING|$(whoami | tr [a-z] [A-Z])|OPENSTACK)"
    exit $ERRBADARGS
}

# check number of arguments
if [ $# -ne 2 ]
then
    print_usage
fi

# check the mode the user is invoking
if [[ "$2" != 'PROD' && \
    "$2" != 'CI' && \
    "$2" != 'STAGING' && \
    "$2" != 'OPENSTACK' && \
    "$2" != "$(whoami | tr [a-z] [A-Z])" ]]
then
    print_usage
fi

cd $(dirname $0)/.. # move up to the packaging script root
readonly DEBNAME="$1"
readonly TARGET_REPOSITORY=$( echo "$2" | tr [A-Z] [a-z] ) # convert to lowercase
readonly CONTROL_FILE=./"$DEBNAME"/DEBIAN/control

check_for_builder_script
compute_new_version_number
create_deb_control_file
build_deb

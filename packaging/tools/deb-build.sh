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
# - "brew install fakeroot" if you don't have fakeroot on your ssytem
#
###############################################################################

# Check that prerequisite exectuables exist(
check_for_executable() {
    # $1 is executable to test for
    # $2 is package name
    which $1 > /dev/null
    if [ $? -ne 0 ] ; then
        case $(uname) in
            Darwin)
                echo "Please install the $2 package from homebrew:"
                echo "    brew install $2"
                ;;
            Linux)
                echo "Please install the $2 package from apt:"
                echo "    sudo apt-get install $2"
                ;;
            *)
                echo "Please install a package to provide $1"
                ;;
        esac
        exit 1
    fi
}

# Check if a expanded deb exists under build/$DEBNAME
# Params:
#   None
# Return:
#   0 if the deb looks okay
#   2 if the folder or control file are missing
function check_for_package_assets_dir() {
    if [ ! -f $CONTROL_FILE ] ;
    then
        echo "Missing $CONTROL_FILE.  Stop."
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
    version_file=$1.current.ver
    wget \
        --output-document $version_file \
        --no-check-certificate -q \
        --no-cache http://apt.aerofs.com/ubuntu/$2/versions/$1.current.ver

    local error_code=$?

    if [ $error_code -eq 0 ]
    then
        cat $version_file
        rm $version_file
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
function add_version_to_deb_control_file() {
    echo "Current $DEBNAME version is $VERSION. New version is $NEW_VERSION."
    echo $NEW_VERSION > aerofs-$DEBNAME.current.ver
    echo "Version: $NEW_VERSION" >> $CONTROL_FILE
}

function rm_docker_files() {
    rm -rf $DEBNAME/Dockerfile $DEBNAME/Makefile $DEBNAME/root $DEBNAME/buildroot
}

# Creates a debian control file
# Params:
#   None
# Returns:
#   0: Successfully created the debian package
#   4: fakeroot failed to run
#   other: Any other failure condition
function build_deb() {
    # We have to chown under the fakeroot environment so the
    # package unpacks to files with root's uid/gid.
    # We use gzip because it's about twice as fast to build the package, and
    # it's (as of this writing) better to have 10% larger packages (that add 42
    # seconds to upload) than to have 50% longer build times (that add 60
    # seconds to package_servers).
    # We set an explicit shell because some users might have SHELL=/bin/zsh
    fakeroot /bin/bash << EOF
    set -e
    chown -R 0:0 $DEBNAME
    dpkg-deb -Zgzip --build $DEBNAME aerofs-${DEBNAME}.deb
EOF
    if [ $? -ne 0 ]
    then
        echo "Failure in fakeroot. (Do you have dpkg and fakeroot installed?)."
        exit $ERRFAKEROOTFAILED
    fi
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
    echo " <repository> repository to upload package to (PUBLIC|PRIVATE|CI|$(whoami | tr [a-z] [A-Z]))"
    exit $ERRBADARGS
}

# check number of arguments
if [ $# -ne 2 ]
then
    print_usage
fi

# check the mode the user is invoking
if [[ "$2" != 'PUBLIC' && \
    "$2" != 'PRIVATE' && \
    "$2" != 'CI' && \
    "$2" != "$(whoami | tr [a-z] [A-Z])" ]]
then
    print_usage
fi

cd $(dirname $0)/../build # move into the build/ directory
readonly DEBNAME="$1"
readonly TARGET_REPOSITORY=$( echo "$2" | tr [A-Z] [a-z] ) # convert to lowercase
readonly CONTROL_FILE=./"$DEBNAME"/DEBIAN/control

check_for_executable dpkg-deb dpkg
check_for_executable fakeroot fakeroot
check_for_package_assets_dir
compute_new_version_number
add_version_to_deb_control_file
rm_docker_files
build_deb

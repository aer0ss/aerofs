#!/bin/bash
# This script is passed:
#   - the path to a requirements.txt
#   - a path to a folder in which to cache sdist files
# and produces:
#   - an sdist folder named <package_name> containing a cache of the PyPI
#     packages fetched

set -e -u

function DieUsage() {
    echo "usage: $0 <package name> <requirements file path>"
    exit 1
}

if [ $# -ne 2 ] ; then
    DieUsage
fi

declare -r packaging_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/.."
declare -r requirements_file="$1"
declare -r cache_path="$2"

case $(uname) in
    Darwin) checksum_program="shasum -a 1"
        ;;
    Linux)  checksum_program="sha1sum"
        ;;
    *)      echo "I don't have a checksum program for your platform.  Stop."
            exit 1 ;;
esac

declare -r requirements_checksum_file="$cache_path/cache-checksum"
declare -r requirements_checksum_line="$( $checksum_program "$requirements_file" )"
declare -r requirements_checksum=${requirements_checksum_line%% *} # strip out everything after the 40 chars of sha1sum

# if $CACHE_PATH/cache-checksum doesn't exist, or has a different sha1sum than
# the source requirements.txt, fetch all the pip source tarballs.
# (There's no good way to do incremental updates.)
if [[ ! -r "$requirements_checksum_file" || "$(cat $requirements_checksum_file)" != "$requirements_checksum" ]] ; then
    echo "Updating cache for $cache_path"
    rm -rf "$cache_path"
    mkdir -p "$cache_path"
    pip install -vvv --no-install --no-deps --ignore-installed --download=$cache_path/ --requirement=$requirements_file > $cache_path/install.log 2>&1
    # Sadly, when pip bails because the build dir already existed, it fails to
    # install the package but still returns 0.  *sigh*
    if grep --quiet "pip can't proceed with requirement" $cache_path/install.log ; then
        echo "pip fetch failed; see $cache_path/install.log for details"
        exit 1
    else
        rm $cache_path/install.log
    fi
    echo $requirements_checksum > "$requirements_checksum_file"
else
    echo "Checksum $requirements_checksum matched cache for $cache_path"
fi

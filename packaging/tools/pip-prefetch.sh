#!/bin/bash
# This script is passed:
#   - the path to a requirements.txt
#   - a path to a folder in which to cache sdist files
# and produces:
#   - an sdist folder named <package_name> containing a cache of the PyPI
#     packages fetched

set -e -u

function DieUsage() {
    echo "usage: $0 <requirements file path> <path in which to store sdist packages>"
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

PackagesForRequirementsAreAllPresent() {
    # returns 0 if all the files specified in $2 are cached in $1.
    # returns 1 otherwise
    # $1 is cache_path
    # $2 is requirements file
    # if $3 is nonempty, then failures will be logged to stdout
    declare arg_cache_path="$1"
    declare arg_requirements_file="$2"
    declare arg_verbose="${3:-}"
    [[ -d "$arg_cache_path" ]] || return 1
    [[ -r "$arg_requirements_file" ]] || return 1
    declare -i failcount=0
    declare -a failures
    for line in $( cat "$arg_requirements_file" ) ; do
        package_underscores="${line/-/_}"
        package_filename_prefix="${package_underscores/==/-}."
        package_filename_prefix_alt="${line/==/-}."
        # We can't know in advance if a package's archive will be a .zip, a
        # .tar.gz, or something else.  It's annoying that we have to use shell
        # globbing here, but I couldn't think of another way to tersely test
        # "does any file that matches this pattern exist".
        set +e
        if ! ( ls "$arg_cache_path/$package_filename_prefix"* > /dev/null 2>&1 ||
            ls "$arg_cache_path/$package_filename_prefix_alt"* > /dev/null 2>&1 ) ; then
            failures[$failcount]=$line
            failcount=$failcount+1
        fi
        set -e
    done
    # Complain about missing packages if verbose mode
    if [[ "$arg_verbose" != "" ]] && [[ $failcount -ne 0 ]] ; then
        echo "Missing $failcount packages:"
        for failure in ${failures[@]}; do
            echo "    $failure"
        done
        echo "See $arg_cache_path/install.log for details."
    fi
    if [[ $failcount -ne 0 ]] ; then
        return 1
    else
        return 0
    fi
}

# if $CACHE_PATH/cache-checksum doesn't exist, or has a different sha1sum than
# the source requirements.txt, fetch all the pip source tarballs.
# (There's no good way to do incremental updates.)
if [[ ! -r "$requirements_checksum_file" ]] || \
   [[ "$(cat $requirements_checksum_file)" != "$requirements_checksum" ]] || \
   ! PackagesForRequirementsAreAllPresent "$cache_path" "$requirements_file"
then
    echo "Updating cache for $cache_path"
    rm -rf "$cache_path"
    mkdir -p "$cache_path"
    # Disable wheels (binary packages); OSX binaries won't run on Linux
    export PIP_USE_WHEEL=0
    pip install -vvv --no-install --no-deps --ignore-installed --download=$cache_path/ --requirement=$requirements_file > $cache_path/install.log 2>&1
    # Sanity check - ensure all packages wound up with a name-matching source archive.
    if PackagesForRequirementsAreAllPresent "$cache_path" "$requirements_file" verbose ; then
        rm $cache_path/install.log
        echo $requirements_checksum > "$requirements_checksum_file"
    else
        echo "See $cache_path/install.log for details."
        exit 1
    fi
else
    echo "Checksum $requirements_checksum matched cache for $cache_path"
    echo "All source packages present and accounted for."
fi

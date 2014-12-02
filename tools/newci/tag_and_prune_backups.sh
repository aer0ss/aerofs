#!/bin/bash
set -u -x

function error_handler {
    echo Error at line $1
    exit 2
}

trap 'error_handler $LINENO' ERR

function DieUsage {
    echo Usage: $0 >&2
    exit 1
}

[[ $# -eq 0 ]] || DieUsage

BACKUPS=~/backups
TAGS="$BACKUPS/tags.txt"
mkdir -p $BACKUPS/tags

echo Fetching tags...
git fetch --tags

# NB: this relies on GNU sort for "version sort"
git tag -l 'private-*' | sort -V | perl -n -e '/private-(.*)/ && print "$1\n"' > $TAGS

# NB: this relies on GNU ls for "version sort"
# https://www.gnu.org/software/coreutils/manual/html_node/Details-about-version-sort.html
latest="$(basename $(ls -v $BACKUPS/tags | tail -n 1) .tar.gz)"

idx=1
if [ -n "$latest" ] ; then
    idx=$(( $(grep -n $latest $TAGS | cut -d':' -f1) + 1))
fi

echo Tagging recent backups...
newest_tag=""
for tag in $(tail -n +$idx $TAGS) ; do
    tag_commit="$(git rev-parse private-$tag)"
    if [ -f $BACKUPS/commits/$tag_commit.tar.gz ] && [ ! -e $BACKUPS/tags/$tag_commit.tar.gz]; then
        echo "Tagged $tag_commit as $tag"
        newest_tag=$tag
        mv $BACKUPS/commits/$tag_commit.tar.gz $BACKUPS/tags/$tag_commit.tar.gz
    else
        echo "WARN: not tagged $tag $tag_commit"
    fi
done

# NB: pruning strategy assumes that future tags won't refer to commits anterior to the most recent tag
if [ -n "$newest_tag" ] ; then
    echo Pruning backups for commits older than $newest_tag
    find $BACKUPS/commits \! -newer $BACKUPS/tags/$newest_tag -delete
fi

rm -f $BACKUPS/tags.txt

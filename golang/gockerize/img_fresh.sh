#!/bin/bash

# $1 image
# $2 source path
# return true if the image is newer than the newest file in the source tree
function img_fresh() {
    local created=$(
        docker inspect --format='{{.Created}}' --type=image "$1" |\
        cut -d. -f1 |\
        sed 's/T/ /'
    )

    # find first file in build context newer than image, if any
    # NB: docker gives UTC timestamps, make sure find does not compare it to local time
    [[ -n "$created" ]] && [[ -e "$2" ]] && [[ -z "$(find "$2" -type f -newermt "$created" | head -n 1)" ]]
}

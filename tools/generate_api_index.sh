#!/bin/bash

# Find swagger specs in the apidocs directory,
# and build a list of swagger-ui links into a single index.html file.
#
# Arguments:
#   -p Prefix directory (parent of the working directory)
#   -d working Directory (place to search for apidocs; will be used in generated urls)
#
# Example:
#   generate_api_index.sh -p docs -d engineering/apidocs

# TODO: styling
# TODO: command-line override of Swagger URI prefix


# I expect to be run in the repo root...
# DEFAULTS: docs/engineering/apidocs (works in repo root that way)
PREFIX=docs/
WORK_DIR=engineering/apidocs
OUTF=$WORK_DIR/index.html
SWAGGER_URI=/swagger-ui/dist

function DoArgs
{
    while getopts "d:p:" OPTION
    do
        case $OPTION in
        d) WORK_DIR=$OPTARG ;;
        p) PREFIX=$OPTARG ;;
        *) Die "Unrecognized argument $OPTARG" ;;
        esac
    done
}


function Die
{
    echo $@;
    exit 1
}

function Main
{
    DoArgs $@;

    here=$PWD
    cd $PREFIX
    echo "Building index.html in $PREFIX/${OUTF}..."

    cat > ${OUTF} << EOD
<html><title>API docs browser</title>
<body>Would you be interested in API-browsing one of the following?
<ul>
EOD

    for x in `find ${WORK_DIR} -type f \( -iname \*.yaml -o -iname \*.yml -o -iname \*.json \) `
    do
        echo Adding ${x} to list
        echo "<li><a href=\"${SWAGGER_URI}?url=/${x}\">${x}</a></li>" >> ${OUTF}
    done

    cat >> ${OUTF} << EOD
</ul>
</body></html>
EOD

    cd $here
}

Main $@;

#!/bin/bash

# 2013/03/19, jP
#   This script simply monitors a folder for changes to Markdown files,
#   i.e., those with a .md suffix - and runs markdown as necessary.
#
#   The generated HTML is enhanced with a header including a title,
#   and a stylesheet.
#
#   This is about as stupid as it can be while still handling
#   filenames that include spaces.
#
#   HTML title is the first line of text in the document, with # characters
#   removed. If the first line is empty, so will be the HTML title.
#
# NOTE:
#   Some sample css's that look ok:
#       http://jasonm23.github.com/markdown-css-themes/markdown.css
#       http://kevinburke.bitbucket.org/markdowncss/markdown.css
#
# 2013/03/20 jP: Fixed a stupid bug where sourceDir never worked.
#       Add CSS support, and HTML head & title elements
# 2013/03/28 jP: Added support for arbitrary CSS source, including from the web
#       Silenced chatter. Reworked so default mode runs only once;
#       Added -f to loop forever.
#

# ========
# User-serviceable parts inside!!
#
SleepVal=2
EchoOn=0
Debug=0
CssSource=
# ========

[ $Debug -ne 0 ] && set -x

# Yay, globals?
typeset CssLink
typeset Forever=0
typeset sourceDir=.
typeset targetDir=.
typeset CssFile=.markdown.css
typeset Recursive=0

function Usage
{
    set +e
    [ $# -gt 0 ] && echo "ERROR: $@"

    echo
    echo "$(basename $0) [-h -v -f] [ -c URI | -c path.css ] [-s seconds] [sourceDir]"
    echo "    -h              Print this help message and exit"
    echo "    -v              Verbosity"
    echo "    -V              Crazy verbosity"
    echo "    -f              Run forever - monitor a directory for updates."
    echo "    -s <seconds>    Scanning period (default $SleepVal) seconds; used with -f"
    echo "    -c URI|path     CSS file: supply either an http URI or path to a local file."
    echo "    -r              Scan the directory recursively. Folder structure will be preserved"
    echo "                    in the target directory."
    echo "    sourceDir       Directory path to watch (default .)"
    echo "    targetDir       Directory path to place output files (default .)"
    echo
    echo "    Monitor sourceDir for changes to Markdown files (named *.md)"
    echo "    When the MarkDown source is newer than the matching .html"
    echo "    file, call $Markdown to update it. "
    echo ""
    echo "    If a CSS link is not provided, or the path given is to a file,"
    echo "    this script will copy the target CSS file to .markdown.css in"
    echo "    the target directory. If an http/https CSS link is given, the"
    echo "    URI will be used as-is."
    echo
}

function Die
{
    echo ERROR: $@
    echo
    exit 1
}

function DoArgs
{
    while getopts "c:fhs:vVr" OPTION
    do
        case $OPTION in
        c)
            CssSource=$OPTARG
            ;;
        f)
            Forever=1
            ;;
        h)
            Usage
            exit 0
            ;;
        s)
            SleepVal=$OPTARG
            ;;
        v)
            EchoOn=1
            ;;
        V)
            Debug=1
            ;;
        r)
            Recursive=1
            ;;
        *)
            Usage ""
            exit 1
            ;;
        esac
    done

    shift $((OPTIND-1))
    [ $# -gt 0 ] && sourceDir="$1"
    [ -d "$sourceDir" ] || Die "Directory $sourceDir does not exist."

    shift 1
    [ $# -gt 0 ] && targetDir="$1"
}

# If not in quiet mode, echo to stdout.
#
function DoEcho
{
    [ $EchoOn -ne 0 ] && echo "$@" ||:
}

#
# If $CssSource is not a http link, then check for the existence of
# $targetDir/$CssFile.
#
# If it is out of date, copy $CssSource to $targetDir/$CssFile.
# If $CssSource is not set, create $targetDir/$CssFile with the here-doc
# in WriteCssFile.
#
# Side-effect: sets global CssLink to either a web URI or a relative file path.
#
# $1 : the target path
#
function CheckCss
{
    [ $Debug -ne 0 ] && set -x
    set -e

    typeset targetDir="$1"
    typeset target

    if [ -z "$CssSource" ]
    then
        target="$targetDir"/$CssFile
        CssLink="$CssFile"

        if [ ! -f "$target" ]
        then
            WriteCssFile "$target"
        fi

    else

        if [ "${CssSource##http}" != "${CssSource}" ]
        then
            CssLink=$CssSource
            return
        else
            target="$targetDir"/$(basename "$CssSource")
            CssLink=$(basename "$CssSource")

            if [ "$CssSource" -nt "$target" ]
            then
                DoEcho "Copying CSS file to $targetDir"
                cp "$CssSource" "$target"
            fi
        fi
    fi
}

# Call ProcessMarkdown if the markdown file is newer than the target html file
# or the target doesn't exist. Create parent folders of the target if necessary.
#
# This method assumes PWD is the folder being scanned.
# $1 : markdown file path
# $2 : target base dir path
#
function CheckMarkdown
{
    typeset m="${1}"
    typeset h="${2}/${1%%.md}.html"

    # Create target directory if necessary
    typeset targetDir=`dirname ${h}`
    [ -d "${targetDir}" ] || mkdir -p "${targetDir}"

    CheckCss ${targetDir}

    if [ "$m" -nt "$h" ]
    then
        CompileMarkdown "$m" "$h"
    fi
}

# $1 : markdown filepath
# $2 : destination (html) filepath
#
function CompileMarkdown
{
    [ $Debug -ne 0 ] && set -x
    [ $# -ne 2 ] && Die "Wrong args to DoMarkdown"
    set -e

    typeset mdfile=$1
    typeset htfile=$2
    typeset LazyTitle=$(head -1 "$mdfile")

    cat > "$htfile" << EOD1
<html>
    <head>
    <link href="${CssLink}" rel="stylesheet"></link>
    <title>${LazyTitle//\#}</title>
    </head>
    <body>
EOD1

    kramdown --no-auto-ids --entity-output :symbolic "$mdfile" >> "$htfile"

    cat >> "$htfile" << EOD2
    </body>
</html>
EOD2
    DoEcho "$(date): Updated file://${PWD}/$htfile"
}

function ScanDir
{
    [ $Debug -ne 0 ] && set -x
    set -e

    typeset pwd=`pwd`
    # Set pwd to the sourceDir to simplify processing in CheckMarkdown
    cd "$sourceDir"

    # prefix the orginal pwd to the target dir if it's not an absolute path
    typeset actualTargetDir
    if [[ "$0" = /* ]]
    then
        actualTargetDir="${targetDir}"
    else
        actualTargetDir="${pwd}/${targetDir}"
    fi

    if [ $Recursive -ne 0 ]
    then
        for x in `find . -iname \*.md -type f`
        do
            CheckMarkdown "$x" "${actualTargetDir}"
        done
    else
        for x in *.md
        do
            [ -f "$x" ] && CheckMarkdown "$x" "${actualTargetDir}"
        done
    fi

    # Restore the old pwd
    cd "$pwd"
}

function Main
{
    DoArgs "$@";

    [ $Debug -ne 0 ] && set -x
    DoEcho "Monitoring directory $sourceDir for changes to Markdown files..."

    ScanDir

    if [ $Forever -ne 0 ]
    then
        echo Watching $sourceDir for MarkDown changes. Ctrl-C to stop watching.
        while true
        do
            ScanDir
            sleep $SleepVal
        done
    fi
}

# Dump a here-doc into the named CSS file.
# $1 : filename
#
function WriteCssFile
{
    [ $# -ne 1 ] && "Wrong args to WriteCssFile"

    cat > $1 << EOCSS
h1,
h2,
h3,
h4,
h5,
h6,
p,
blockquote {
    margin: 0;
    padding: 0;
}
body {
    font-family: "Helvetica Neue", Helvetica, "Hiragino Sans GB", Arial, sans-serif;
    font-size: 13px;
    line-height: 18px;
    // color: #737373;
    background-color: white;
    margin: 10px 13px 10px 13px;
}
table {
    margin: 10px 0 15px 0;
    border-collapse: collapse;
}
td,th {
    border: 1px solid #ddd;
    padding: 3px 10px;
}
th {
    padding: 5px 10px;
}

a {
    color: #0069d6;
}
a:hover {
    color: #0050a3;
    text-decoration: none;
}
a img {
    border: none;
}
p {
    margin-bottom: 9px;
}
h1,
h2,
h3,
h4,
h5,
h6 {
    color: #404040;
    line-height: 36px;
}
h1 {
    margin-bottom: 18px;
    font-size: 30px;
}
h2 {
    font-size: 24px;
}
h3 {
    font-size: 18px;
}
h4 {
    font-size: 16px;
}
h5 {
    font-size: 14px;
}
h6 {
    font-size: 13px;
}
hr {
    margin: 0 0 19px;
    border: 0;
    border-bottom: 1px solid #ccc;
}
blockquote {
    padding: 13px 13px 21px 15px;
    margin-bottom: 18px;
    font-family:georgia,serif;
    font-style: italic;
}
blockquote:before {
    content:"\201C";
    font-size:40px;
    margin-left:-10px;
    font-family:georgia,serif;
    color:#eee;
}
blockquote p {
    font-size: 14px;
    font-weight: 300;
    line-height: 18px;
    margin-bottom: 0;
    font-style: italic;
}
code, pre {
    font-family: Monaco, Andale Mono, Courier New, monospace;
}
code {
    background-color: #fee9cc;
    color: rgba(0, 0, 0, 0.75);
    padding: 1px 3px;
    font-size: 12px;
    -webkit-border-radius: 3px;
    -moz-border-radius: 3px;
    border-radius: 3px;
}
pre {
    display: block;
    padding: 14px;
    margin: 0 0 18px;
    line-height: 16px;
    font-size: 11px;
    border: 1px solid #d9d9d9;
    white-space: pre-wrap;
    word-wrap: break-word;
}
pre code {
    background-color: #fff;
    color:#737373;
    font-size: 11px;
    padding: 0;
}
sup {
    font-size: 0.83em;
    vertical-align: super;
    line-height: 0;
}
* {
    -webkit-print-color-adjust: exact;
}
@media screen and (min-width: 914px) {
    body {
        width: 854px;
        margin:10px auto;
    }
}
@media print {
    body,code,pre code,h1,h2,h3,h4,h5,h6 {
        color: black;
    }
    table, pre {
        page-break-inside: avoid;
    }
}
EOCSS
}

Main "$@";


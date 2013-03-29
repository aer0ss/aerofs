#!/bin/bash

# 2013/03/19, jP
#   This script simply monitors $PWD for changes to Markdown files,
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
# 2013/03/20 jP: Fixed a stupid bug where TargetDir never worked.
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
#
# NOTE:
#   Some sample css's that look ok:
#       http://jasonm23.github.com/markdown-css-themes/markdown.css
#       http://kevinburke.bitbucket.org/markdowncss/markdown.css
#
# We need to find Markdown.pl from somewhere; let's expect it in the
# directory this script ran from. Override this for other Markdown
# implementations.
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
Markdown=$DIR/Markdown.pl
# ========

[ $Debug -ne 0 ] && set -x

# Yay, globals?
typeset CssLink
typeset Forever=0
typeset TargetDir=.
typeset CssFile=.markdown.css

function Usage
{
    set +e
    [ $# -gt 0 ] && echo "ERROR: $@"

    echo
    echo "$(basename $0) [-h -v -f] [ -c URI | -c path.css ] [-s seconds] [targetDir]"
    echo "    -h              Print this help message and exit"
    echo "    -v              Verbosity"
    echo "    -V              Crazy verbosity"
    echo "    -f              Run forever - monitor a directory for updates."
    echo "    -s <seconds>    Scanning period (default $SleepVal) seconds; used with -f"
    echo "    -c URI|path     CSS file: supply either an http URI or path to a local file."
    echo "    targetDir       Directory path to watch (default .)"
    echo 
    echo "    Monitor targetDir for changes to Markdown files (named *.md)"
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
    while getopts "c:fhs:vV" OPTION
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
        *)
            Usage ""
            exit 1
            ;;
        esac
    done

    shift $((OPTIND-1))

    TargetDir=.
    [ $# -gt 0 ] && TargetDir="$1"
    [ -d "$TargetDir" ] || Die "Directory $TargetDir does not exist."
}

# If not in quiet mode, echo to stdout.
#
function DoEcho
{
    [ $EchoOn -ne 0 ] && echo "$@" ||:
}

#
# If $CssSource is not a http link, then check for the existence of
# $TargetDir/$CssFile. 
#
# If it is out of date, copy $CssSource to $TargetDir/$CssFile.
# If $CssSource is not set, create $TargetDir/$CssFile with the here-doc
# in WriteCssFile.
#
# Side-effect: sets global CssLink to either a web URI or a relative file path.
#
function CheckCss
{
    [ $Debug -ne 0 ] && set -x
    set -e

    typeset destpath

    if [ -z "$CssSource" ]
    then
        destpath="$TargetDir"/$CssFile
        CssLink="$CssFile"

        if [ ! -f "$destpath" ]
        then
            WriteCssFile "$destpath"
        fi

    else

        if [ "${CssSource##http}" != "${CssSource}" ] 
        then
            CssLink=$CssSource
            return
        else
            destpath="$TargetDir"/$(basename "$CssSource")
            CssLink=$(basename "$CssSource")

            if [ "$CssSource" -nt "$destpath" ]
            then
                DoEcho "Copying CSS file to $TargetDir"
                cp "$CssSource" "$destpath"
            fi
        fi
    fi
}

# $1 : markdown filename
# $2 : destination (html) filename
#
function ProcessMarkdown
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

    "$Markdown" "$mdfile" >> "$htfile"

    cat >> "$htfile" << EOD2
    </body>
</html>
EOD2
    DoEcho "$(date): Updated file://$(PWD)/$htfile"
}

#
#   $1 : target directory
#
function ScanDir
{
    [ $Debug -ne 0 ] && set -x
    set -e

    typeset target="$1"

    for x in "$target"/*.md
    do
        typeset m="${x}"
        typeset h="${x%%.md}.html"

        if [ "$m" -nt "$h" ]
        then
            ProcessMarkdown "$m" "$h"
        fi
    done
}

function Main
{
    DoArgs "$@";

    [ $Debug -ne 0 ] && set -x
    DoEcho "Monitoring directory $TargetDir for changes to Markdown files..."

    CheckCss
    ScanDir "$TargetDir"

    while [ $Forever -ne 0 ]
    do
        ScanDir "$TargetDir"
        sleep $SleepVal
    done
}

# Dump a here-doc into the named CSS file.
# $1 : filename
#
function WriteCssFile
{
    [ $# -ne 1 ] && "Wrong args to WriteCssFile"

    cat > $1 << EOCSS 
body {
	font-family: "Avenir Next", Helvetica, Arial, sans-serif;
	padding:1em;
	margin:auto;
	max-width:48em;
	background:#fefefe;
}

h1, h2, h3, h4, h5, h6 {
	font-weight: bold;
}

h1 {
	color: #000000;
	font-size: 28pt;
}

h2 {
	border-bottom: 1px solid #CCCCCC;
	color: #000000;
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
	color: #777777;
	background-color: inherit;
	font-size: 14px;
}

hr {
	height: 0.2em;
	border: 0;
	color: #CCCCCC;
	background-color: #CCCCCC;
}

p, ul, ol, dl, li, table, pre {
	margin: 15px 0;
}

blockquote {
	margin: 15px 0;
    padding-left: 3em;
    border-left: 0.5em #EEE solid;
}

a, a:visited {
	color: #4183C4;
	background-color: inherit;
	text-decoration: none;
}

#message {
	border-radius: 6px;
	border: 1px solid #ccc;
	display:block;
	width:100%;
	height:60px;
	margin:6px 0px;
}

button, #ws {
	font-size: 10pt;
	padding: 4px 6px;
	border-radius: 5px;
	border: 1px solid #bbb;
	background-color: #eee;
}

code, pre, #ws, #message {
	font-family: Monaco;
	font-size: 10pt;
	border-radius: 3px;
	background-color: #F8F8F8;
	color: inherit;
}

code {
	border: 1px solid #EAEAEA;
	margin: 0 2px;
	padding: 0 5px;
}

pre {
	border: 1px solid #CCCCCC;
	overflow: auto;
	padding: 4px 8px;
}

pre > code {
	border: 0;
	margin: 0;
	padding: 0;
}

#ws { background-color: #f8f8f8; }

.send { color:#77bb77; }
.server { color:#7799bb; }
.error { color:#AA0000; }
EOCSS
}

Main "$@";


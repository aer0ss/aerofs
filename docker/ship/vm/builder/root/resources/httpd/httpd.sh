#!/usr/bin/env bash
# Copyright & license: https://github.com/avleen/bashttpd/blob/master/LICENSE
warn() { echo "WARNING: $@" >&2; }

[ -r httpd.conf ] || {
   warn "httpd.conf doesn't exist."
   exit 1
}

recv() { echo "< $@" >&2; }
send() { echo "> $@" >&2;
         printf '%s\r\n' "$*"; }

DATE=$(date +"%a, %d %b %Y %H:%M:%S %Z")
declare -a RESPONSE_HEADERS=(
    "Date: $DATE"
    "Expires: $DATE"
    "Server: Slash Bin Slash Bash"
)

add_response_header() {
   RESPONSE_HEADERS+=("$1: $2")
}

declare -a HTTP_RESPONSE=(
   [200]="OK"
   [400]="Bad Request"
   [403]="Forbidden"
   [404]="Not Found"
   [405]="Method Not Allowed"
   [500]="Internal Server Error"
)

send_response() {
   local code=$1
   send "HTTP/1.0 $1 ${HTTP_RESPONSE[$1]}"
   for i in "${RESPONSE_HEADERS[@]}"; do
      send "$i"
   done
   send
   while read -r line; do
      send "$line"
   done
}

send_response_ok_exit() { send_response 200; exit 0; }

fail_with() {
   send_response "$1" <<< "$1 ${HTTP_RESPONSE[$1]}"
   exit 1
}

serve_file() {
   local file=$1

   CONTENT_TYPE=
   case "$file" in
     *\.css)
       CONTENT_TYPE="text/css"
       ;;
     *\.js)
       CONTENT_TYPE="text/javascript"
       ;;
     *\.json)
       CONTENT_TYPE="application/json"
       ;;
     *\.html)
       CONTENT_TYPE="text/html"
       ;;
   esac

   add_response_header "Content-Type"   "$CONTENT_TYPE";

   send_response_ok_exit < "$file"
}

serve_dir_with_tree()
{
   local dir="$1" tree_vers tree_opts basehref x

   add_response_header "Content-Type" "text/html"

   # The --du option was added in 1.6.0.
   read x tree_vers x < <(tree --version)
   [[ $tree_vers == v1.6* ]] && tree_opts="--du"

   send_response_ok_exit < \
      <(tree -H "$2" -L 1 "$tree_opts" -D "$dir")
}

serve_dir_with_ls()
{
   local dir=$1

   add_response_header "Content-Type" "text/plain"

   send_response_ok_exit < \
      <(ls -la "$dir")
}

serve_dir() {
   local dir=$1

   which tree &>/dev/null && \
      serve_dir_with_tree "$@"

   serve_dir_with_ls "$@"

   fail_with 500
}

serve_dir_or_file_from() {
   local URL_PATH=$1/$3
   shift

   # sanitize URL_PATH
   URL_PATH=${URL_PATH//[^a-zA-Z0-9_~\-\.\/]/}
   [[ $URL_PATH == *..* ]] && fail_with 400

   # Serve index file if exists in requested directory
   [[ -d $URL_PATH && -f $URL_PATH/index.html && -r $URL_PATH/index.html ]] && \
      URL_PATH="$URL_PATH/index.html"

   if [[ -f $URL_PATH ]]; then
      [[ -r $URL_PATH ]] && \
         serve_file "$URL_PATH" "$@" || fail_with 403
   elif [[ -d $URL_PATH ]]; then
      [[ -x $URL_PATH ]] && \
         serve_dir  "$URL_PATH" "$@" || fail_with 403
   fi

   fail_with 404
}

serve_static_string() {
   add_response_header "Content-Type" "text/plain"
   send_response_ok_exit <<< "$1"
}

on_uri_match() {
   local regex=$1
   shift

   [[ $REQUEST_URI =~ $regex ]] && \
      "$@" "${BASH_REMATCH[@]}"
}

unconditionally() {
   "$@" "$REQUEST_URI"
}

read -r line || fail_with 400

line=${line%%$'\r'}
recv "$line"

read -r REQUEST_METHOD REQUEST_URI REQUEST_HTTP_VERSION <<<"$line"

[ -n "$REQUEST_METHOD" ] && \
[ -n "$REQUEST_URI" ] && \
[ -n "$REQUEST_HTTP_VERSION" ] \
   || fail_with 400

[ "$REQUEST_METHOD" = "GET" ] || fail_with 405

declare -a REQUEST_HEADERS

while read -r line; do
   line=${line%%$'\r'}
   recv "$line"

   # If we've reached the end of the headers, break.
   [ -z "$line" ] && break

   REQUEST_HEADERS+=("$line")
done

source "${BASH_SOURCE[0]%/*}"/httpd.conf
fail_with 500
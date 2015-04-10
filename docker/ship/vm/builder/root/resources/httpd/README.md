httpd.sh is adapted from https://github.com/avleen/bashttpd with the following modifications.
[View bashttpd's copyright notice and license](https://github.com/avleen/bashttpd/blob/master/LICENSE).

- Removed Content-Length header because leading whitespaces of a file are not sent in the response, causing incorrect
length calculation. TODO (WW): fix the root cause.

- Removed MIME type detection as CoreOS doesn't have the `file` command.

- Removed Comments to keep cloud-config small.

- Renamed bashttpd.conf to httpd.conf

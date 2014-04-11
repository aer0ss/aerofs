# AeroFS OAuth client registration tool

This script signs in to AeroFS with a username/credential pair and generates
a durable OAuth access token.

## Installation

You can use your system python, or a virtualenv or similar environment.

This depends on the `protobuf` and `requests` libraries.

Example:
    virtualenv testing
    ./testing/bin/pip install protobuf
    ./testing/bin/pip install requests
    ./testing/bin/python aero-auth/main.py -h

## Usage

Using the `site.cfg.template` as a starting point, generate a site.cfg file
for your AeroFS private cloud environment. The path and filename can be overridden
with the `-c {filename}` command-line argument. Otherwise, a file named site.cfg
in the current directory is expected.

    python aero-oauth/main.py -c mysite.cfg user@example.com password

## Scopes

This version of the tool does not support configurable scopes. OAuth tokens are
generated with "files.read" and "files.write" privilege.


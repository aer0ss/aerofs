#!/usr/bin/python

"""
Certificate Authority HTTP Server.

This script runs a local-only web server that provides CA functionality via a
CGI interface. This script is intended to be run behing a nginx reverse proxy on
the locked down CA server.

At the most basic level, this server is a wrapper for standard openssl commands.
"""

import sys
import server

if __name__ == "__main__":
    cadir = '/opt/ca/prod'
    port = 9002

    ca = server.CertificateAuthorityServer(cadir, port)
    print "Serving locally on port " + str(port) + "..."

    try:
        ca.serve_forever()
    except KeyboardInterrupt, e:
        print ''
        print 'Aborted.'

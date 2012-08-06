#!/usr/bin/python

"""
Certificate Authority HTTP Server.

This script runs a local-only web server that provides CA functionality via a
CGI interface. This script is intended to be run behing a nginx reverse proxy on
the locked down CA server.

At the most basic level, this server is a wrapper for standard openssl commands.
"""

import sys
import aerofs.certauth.server

def usage():
    print "usage: " + sys.argv[0] + " <cadir> <port>"
    sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        usage()

    cadir = sys.argv[1]
    port = sys.argv[2]

    ca = aerofs.certauth.server.CertificateAuthorityServer(cadir, int(port))
    print "Serving locally on port " + port + "..."

    try:
        ca.serve_forever()
    except KeyboardInterrupt, e:
        print ''
        print 'Aborted.'

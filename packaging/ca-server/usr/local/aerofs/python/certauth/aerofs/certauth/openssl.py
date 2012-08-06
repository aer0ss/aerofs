"""
Classes related to executing openssl functions.
"""

import os
import time
import datetime
import subprocess

"""
Parent class for all OpenSSL exceptions.
"""
class ExOpenSSL(Exception):

    def __init__(self, message):
        self._message = message
    def __str__(self):
        return str(self._message)

"""
Internal server error exception (500).
"""
class ExInternalServerError(ExOpenSSL):

    def __init__(self, cadir):
        ExOpenSSL.__init__(self, cadir)

"""
Bad request exception (400).
"""
class ExBadRequest(ExOpenSSL):

    def __init__(self, cadir):
        ExOpenSSL.__init__(self, cadir)

"""
Wrapper for low level OpenSSL commands.
"""
class OpenSSLWrapper(object):

    def __init__(self, cadir):
        self._cadir = cadir

    """
    Some utilities for creating the required filenames, etc.
    """
    def _csr(self, certname):
        return os.path.join(self._cadir, certname + '.csr')
    def _cert(self, certname):
        return os.path.join(self._cadir, certname + '.cert')
    def _time(self):
        dt = datetime.datetime.utcfromtimestamp(int(time.time()))
        return dt.strftime('%Y%m%d%H%M%SZ')
    def _openssl_cnf(self):
        return os.path.join(self._cadir, 'openssl.cnf')

    """
    Create a new signed certificate.
    """
    def newcert(self, certname, csr):
        # Create the cert file from the HTTP POST.
        fh = open(self._csr(certname), 'w')
        fh.write(csr)
        fh.close()

        cmd = ['/usr/bin/openssl', 'ca', '-batch', '-config',
            self._openssl_cnf(), '-notext', '-startdate', self._time(),
            '-passin', 'env:capass', '-in', self._csr(certname), '-out',
            self._cert(certname)]

        try:
            print 'Execute OpenSSL: ' + ' '.join(cmd)
            code = subprocess.call(cmd)
            if code != 0:
                raise ExInternalServerError("OpenSSL error (code=" +
                    str(code) + ").")

        except subprocess.CalledProcessError, e:
            raise ExInternalServerError("Subprocess error.")

        fh = open(self._cert(certname))
        cert = fh.read()
        fh.close()

        return cert

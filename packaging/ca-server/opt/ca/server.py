"""
Classes related to the certificate authority server implementation.
"""

import os
import socket
import wsgiref.simple_server

import openssl

"""
Application object parameter to make_server. Does HTTP request handling.
"""
class ApplicationObject(object):

    def __init__(self, cadir):
        self._cadir = cadir
        self._openssl = openssl.OpenSSLWrapper(cadir)

    def _set_response(self, response, code, length):
        response(code,[
            ("content-type", "text/plain"),
            ("content-length", str(length))])

    def __call__(self, environ, response):

        path_info = environ['PATH_INFO']
        query_string = environ['QUERY_STRING']

        if len(os.path.split(path_info)) > 0 and os.path.split(path_info)[-1] == "cacert.pem":
            cacert = ""
            with open(self._cadir + '/cacert.pem') as cafile:
                for line in cafile:
                    cacert += line

            self._set_response(response, "200 OK", len(cacert))
            return cacert

        try:
            try:
                request_body_size = int(environ['CONTENT_LENGTH'])
                csr = environ['wsgi.input'].read(request_body_size)
            except ValueError:
                raise openssl.ExBadRequest("No POST body");

            certname = 'certs/' + query_string
            ret =  self._openssl.newcert(certname, csr)

        # Catch certain exceptions for debugging purposes. Don't catch
        # everything (like file read errors etc.) - that would be a bit overkill
        # since the WSGI app catches those anyway.
        except openssl.ExBadRequest, e:
            print "Bad request: " + str(e)
            self._set_response(response, "400 Bad Request", 0)
            return ""

        except openssl.ExInternalServerError, e:
            print "Internal server error: " + str(e)
            self._set_response(response, "500 Internal Server Error", 0)
            return ""

        self._set_response(response, "200 OK", len(ret))
        return ret

"""
Really just a simple wrapper for the WSGI (web server gateway interface)
python standard reference implementation.
"""
class CertificateAuthorityServer(object):

    def __init__(self, cadir, port):
        self._httpd = wsgiref.simple_server.make_server("localhost", port,
            ApplicationObject(cadir))

    def serve_forever(self):
        self._httpd.serve_forever()

"""
Classes related to the certificate authority server implementation.
"""

import socket
import wsgiref.simple_server
import aerofs.certauth.openssl

"""
Application object parameter to make_server. Does HTTP request handling.
"""
class ApplicationObject(object):

    def __init__(self, cadir):
        self._openssl = aerofs.certauth.openssl.OpenSSLWrapper(cadir)

    def _set_response(self, response, code, length):
        response(code,[
            ("content-type", "text/plain"),
            ("content-length", str(length))])

    def __call__(self, environ, response):

        # Parse the query string.
        #
        # Acceptable commands:
        #   newcert=<certname>       (generate a new certificate)
        #
        # Other commands can be added in the future if needed...

        query_string = environ['QUERY_STRING']
        query_string_partition = query_string.partition("=")
        command = query_string_partition[0]
        certname = query_string_partition[2]

        try:
            if command == "newcert":
                # Only the newcert command requires POST body, which contains
                # the CSR.
                try:
                    request_body_size = int(environ['CONTENT_LENGTH'])
                    csr = environ['wsgi.input'].read(request_body_size)
                except ValueError:
                    raise aerofs.certauth.openssl.ExBadRequest("No POST body");
                ret =  self._openssl.newcert(certname, csr)

            else:
                raise aerofs.certauth.openssl.ExBadRequest("No such command");
                return ""

        # Catch certain exceptions for debugging purposes. Don't catch
        # everything (like file read errors etc.) - that would be a bit overkill
        # since the WSGI app catches those anyway.
        except aerofs.certauth.openssl.ExBadRequest, e:
            print "Bad request: " + str(e)
            self._set_response(response, "400 Bad Request", 0)
            return ""

        except aerofs.certauth.openssl.ExInternalServerError, e:
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

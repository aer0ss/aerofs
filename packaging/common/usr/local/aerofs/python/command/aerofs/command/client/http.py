import httplib

class HttpCommandRequest(object):
    """
    Class to wrap in HTTP requests to the command server.
    """

    def __init__(self, url, port):
        self._url = url
        self._port = port

    def post(self, bytes):
        headers = {'content-type' : 'text/plain', 'content-length' : len(bytes)}
        conn = httplib.HTTPConnection(self._url, self._port, 1)
        conn.request('POST', '/', bytes, headers)
        return conn.getresponse()

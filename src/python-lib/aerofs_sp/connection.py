import logging
import re
import base64

import requests
from aerofs_common.exception import ExceptionReply


log = logging.getLogger(__name__)

class SyncConnectionService(object):
    """
    Define a synchronous RPC connection
    """
    def __init__(self, http_url, protocol_version, cookies=None, cacert=None):
        """
        http_url is a string starting with "https://"
        protocol_version an int representing the version shared w the server
        cacert is the path of the appliance cacert to authenticate connections with
        """
        self._url = http_url
        self._param_protocol = 'protocol_vers'
        self._param_data = 'data'
        self._protocol_version = protocol_version
        self._session = requests.Session()
        self._verify = cacert if cacert else False
        if cookies:
            self._session.cookies = cookies

    def do_rpc(self, bytes):
        post_data = {self._param_protocol : self._protocol_version,
                     self._param_data : base64.b64encode(bytes)}

        # Verification is only enabled here if a CA certificate was passed in
        r = self._session.post(self._url, data=post_data, verify=self._verify)

        if r.status_code != 200:
            # SP server returned an HTTP error
            # If it was caused by a Java exception on the servlet, the response will include the exception message
            # inside a <pre> block. We try to grab it to use as an error message for our exception.
            errMsg = ""
            match = re.search("<pre>(.*)\n", r.content) # match the first line inside the <pre>, the rest is a stack trace
            if match:
                errMsg = match.group(1)

            log.error("Server returned HTTP code %i - %s" % (r.status_code, errMsg))
            raise Exception("Server returned HTTP code %i - %s" % (r.status_code, errMsg))

        # SP replied with code 200, decode the response it and return it
        decoded = base64.b64decode(r.content)
        return decoded

    def decode_error(self, reply):
        """
        Return an ExErrorReply exception
        """
        return ExceptionReply(reply)

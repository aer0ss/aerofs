"""
This module contains all common code related to protobuf implementations.
"""

import ssl
import time
import socket
import inception.common.network


class NetworkImplException(Exception):
    """
    General network client, used for direct access to a protobuf manager over the network.
    """

    def __init__(self, error_message):
        self._error_message = error_message

    def __str__(self):
        return self.error_message()

    def error_message(self):
        return self._error_message

class NetworkImpl(object):

    def __init__(self, sock):
        self._sock = sock

    def do_rpc(self, bytes):
        inception.common.network.send_message(self._sock, bytes)
        return inception.common.network.receive_message(self._sock)

    def decode_error(self, error_message):
        return NetworkImplException(error_message.errorMessage)


class NetworkConnectImpl(NetworkImpl):
    """
    More specialized class that creates the required socket based on the input host and port.
    """

    # Timeout for connect(), etc.
    CONST_SOCKET_TIMEOUT = 5.0

    def __init__(self, host, port, cert_key_file):
        self._host = host
        self._port = port
        self._sock = None
        self._service = None
        self._cert_key_file = cert_key_file

        # And create the connection.
        self.connect()
        NetworkImpl.__init__(self, self._sock)

    def connect(self):
        """
        Connect to the server application as a client.
        """

        addr = (self._host, self._port)
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        self._sock = ssl.wrap_socket(self._sock,
                ca_certs=self._cert_key_file,
                certfile=self._cert_key_file,
                keyfile=self._cert_key_file,
                cert_reqs=ssl.CERT_REQUIRED)

        self._sock.settimeout(NetworkConnectImpl.CONST_SOCKET_TIMEOUT)
        self._sock.connect((addr))


    def disconnect(self):
        """
        Disconnect and clean up sockets.
        """

        if self._sock != None:
            self._sock.close()
            self._sock = None

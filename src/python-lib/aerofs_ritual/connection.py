import struct
import socket
import sys

from aerofs_common.exception import ExceptionReply


# Define a synchronous RPC connection
class SyncConnectionService(object):

    def __init__(self, socket_file):
        if 'win32' in sys.platform.lower():
            self._socket = WinSyncConnectionService(socket_file)
        else:
            self._socket = NixSyncConnectionService(socket_file)

    def _write(self, bytes):
        # Format the length of the payload into wire format (Big-endian)
        wirelen = struct.pack('>I', len(bytes))
        self._socket.write(wirelen, bytes)

    def _read(self):
        # Gather the response message length
        data_buffer = b''
        buffer_size = 0
        while buffer_size < 4:
            buf = self._socket.read(4)
            if not buf:
                raise socket.error('Failed to read from socket')

            data_buffer += buf
            buffer_size += len(buf)

        message_size = struct.unpack('>I', data_buffer[:4])[0]
        # Gather the response message
        data_buffer = data_buffer[4:]
        buffer_size -= 4
        while buffer_size < message_size:
            buf = self._socket.read(message_size)
            if not buf:
                raise socket.error('Failed to read from socket')

            data_buffer += buf
            buffer_size += len(buf)

        assert buffer_size == message_size
        return data_buffer

    def do_rpc(self, bytes):
        self._write(bytes)
        return self._read()

    def decode_error(self, reply):
        """
        Return an ExErrorReply exception
        """
        return ExceptionReply(reply)

class WinSyncConnectionService(object):

    def __init__(self, socket_file):
        self._socket = open(socket_file, 'r+b', 0)

    def write(self, wirelen, bytes):
        # Prepend the length of the payload and send it over the socket
        # Omitting the seek(0) will cause an IOError #0. We need to perform
        # a seek before writing(because of any prev reads) and after writing
        # for any subsequent reads.
        self._socket.seek(0)
        self._socket.write(b"%s%s" % (wirelen, bytes))
        self._socket.seek(0)

    def read(self, size):
        buf = self._socket.read(size)
        return buf

class NixSyncConnectionService(object):

    def __init__(self, socket_file):
        self._socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._socket.connect(socket_file)

    def write(self, wirelen, bytes):
        # Prepend the length of the payload and send it over the socket
        self._socket.sendall(b"%s%s" % (wirelen, bytes))

    def read(self, size):
        buf = self._socket.recv(size)
        return buf


import struct
import socket

from aerofs_common.exception import ExceptionReply


# Define a synchronous RPC connection
class SyncConnectionService(object):
    _MAX_BUF_SIZE = 8192

    def __init__(self, rpc_host_addr, rpc_host_port):
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.connect((rpc_host_addr, rpc_host_port))

    def do_rpc(self, bytes):
        # Format the length of the payload into wire format (Big-endian)
        wirelen = struct.pack('>I', len(bytes))

        # Prepend the length of the payload and send it over the socket
        self._socket.sendall(b"%s%s" % (wirelen, bytes))

        # Gather the response message length
        data_buffer = b''
        buffer_size = 0
        while buffer_size < 4:
            buf = self._socket.recv(self._MAX_BUF_SIZE)
            if not buf:
                raise socket.error('Failed to read from socket')
            data_buffer += buf
            buffer_size += len(buf)

        message_size = struct.unpack('>I', data_buffer[:4])[0]

        # Gather the response message
        data_buffer = data_buffer[4:]
        buffer_size -= 4
        while buffer_size < message_size:
            buf = self._socket.recv(self._MAX_BUF_SIZE)
            if not buf:
                raise socket.error('Failed to read from socket')
            data_buffer += buf
            buffer_size += len(buf)

        assert buffer_size == message_size
        return data_buffer

    def decode_error(self, reply):
        """
        Return an ExErrorReply exception
        """
        return ExceptionReply(reply)

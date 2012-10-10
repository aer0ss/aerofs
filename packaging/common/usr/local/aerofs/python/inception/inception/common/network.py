import struct
import socket

# Header format (each letter is a byte):
# VVXX LLLL
# where:
#   VV = version
#   XX = reserved
#   LLLL = length

HEADER_LENGTH = 8
HEADER_VERSION = 1

# Set up the first half of the header.
HEADER = bytearray(4)
struct.pack_into("!H", HEADER, 0, HEADER_VERSION)

#
# Some utils for creating and decoding headers. (Should not be needed publicly).
#

def get_version(received_header):
    # ! = network byte order, H = unsigned short (16-bit).
    return struct.unpack_from("!H", received_header)[0]

def get_length(received_header):
    # network byte order, I = unsigned int (32-bit).
    return struct.unpack_from("!I", received_header, 4)[0]

#
# Public utils for sending/receiving messages on an inception network channel.
#

def create_header(length):
    header = bytearray(HEADER_LENGTH)
    for i in range(4):
        header[i] = HEADER[i]
    struct.pack_into("!I", header, 4, length)
    return header

def send_message(sock, bytes):
    header = create_header(len(bytes))
    sock.send(header)
    sock.send(bytes)

def receive_message(sock):
        header = sock.recv(HEADER_LENGTH)

        # Check the header length.
        if len(header) == 0 or len(header) != HEADER_LENGTH:
            raise socket.error('Connection severed')

        # Check the header version.
        version = get_version(header)
        if version != HEADER_VERSION:
            raise socket.error('Header version not correct')

        # Get the payload and check the length.
        length = get_length(header)
        payload = sock.recv(length)

        if len(payload) != length:
            raise socket.error('Payload length not correct')

        # Return the payload.
        return payload

def read_addr_file(addr_file, port):
    """
    Common address reading function. Reads a host addr out of a file. Might throw an IOError or a
    socket.error.
    """

    fh = open(addr_file)
    server = fh.readline().strip()
    ip = socket.gethostbyname(server)
    return (ip,port)

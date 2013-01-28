import socket
import ssl
import struct
import aerofs.command.gen.cmd_pb2
import aerofs.command.gen.verkehr_pb2

class TransientCommandClient(object):
    """
    This class is an interface to the verkehr command channel.
    FIXME: can improve this class such that we can keep the connection open.
    """

    _socket_timeout = 5 # seconds

    def __init__(self, cmd_host, cmd_port):
        self._cmd_addr = (cmd_host, cmd_port)

    # Does a fresh connect each time, so we don't have to manage the connection and handle
    # reconnects, etc. Since the command server does not have high throughput this is okay.
    #
    # Returns true when the send is successful and false otherwise.
    def send(self, user_email, command_bytes):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        # We assume the firewall takes care of everything for us, so don't require certs.
        ssl_sock = ssl.wrap_socket(sock,
            cert_reqs=ssl.CERT_NONE,
            ssl_version=ssl.PROTOCOL_SSLv3)

        ssl_sock.settimeout(self._socket_timeout)
        ssl_sock.connect((self._cmd_addr))

        pb_bytes = TransientCommandClient._to_pb(user_email, command_bytes)

        # Be sure to include the netty length header, so they can properly parse our request.
        ssl_sock.send(struct.pack(">H1", (len(pb_bytes))) + pb_bytes)

        # We get a heartbeat on connect and then we get an ack for our request.
        ack = ssl_sock.read()
        ssl_sock.close()

        return len(ack) > 0

    # Take a list of serialized commands and the user email and convert them to the required
    # verkehr message.
    @staticmethod
    def _to_pb(user_email, command_bytes):
        commands = aerofs.command.gen.cmd_pb2.TransientCommands()

        for command_byte in command_bytes:
            command = aerofs.command.gen.cmd_pb2.TransientCommand()
            command.ParseFromString(command_byte)
            commands.commands.extend([command])

        verkehr_message = aerofs.command.gen.verkehr_pb2.VerkehrMessage()
        verkehr_message.type = aerofs.command.gen.verkehr_pb2.VerkehrMessage.REQUEST

        request = verkehr_message.request
        request.type = aerofs.command.gen.verkehr_pb2.Request.COMMAND
        request.seq_num = 0 # Doesn't matter since we use a different socket every time.

        command_call = request.command_call
        command_call.type = aerofs.command.gen.verkehr_pb2.CommandCall.DELIVER_PAYLOAD

        deliver_payload_call = command_call.deliver_payload_call
        deliver_payload_call.recipient = user_email
        deliver_payload_call.payload = commands.SerializeToString()

        return verkehr_message.SerializeToString()

"""
This module provides tools such that it is easy to create a very clean command line script that
exposes the different types of commands available.
"""

import aerofs.command.gen.cmd_pb2

# Some nice enums for the different types of commands that we expose to the CLI.
class Enum(set):
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError
    def __str__(self):
        result = ''
        for name in self:
            result += name
            result += ' '
        return result

Commands = Enum([
    'UPLOAD_DATABASE',
    'CHECK_UPDATE',
    'SEND_DEFECT',
    'LOG_THREADS'])

class CommandPayload(object):
    """
    A class that represents the payload of a command message.
    """

    def __init__(self, type):
        # We require smart users! They should make sure the type they provide is indeed a valid
        # commands enum.
        assert type in Commands

        if type == Commands.UPLOAD_DATABASE:
            self._type = aerofs.command.gen.cmd_pb2.UPLOAD_DATABASE
        elif type == Commands.CHECK_UPDATE:
            self._type = aerofs.command.gen.cmd_pb2.CHECK_UPDATE
        elif type == Commands.SEND_DEFECT:
            self._type = aerofs.command.gen.cmd_pb2.SEND_DEFECT
        elif type == Commands.LOG_THREADS:
            self._type = aerofs.command.gen.cmd_pb2.LOG_THREADS
        else:
            # Should never reach here because of the first assert, assuming all enums are handled.
            assert False, 'unhandled command type'

    def get_type(self):
        return self._type

class CommandRequest(object):
    """
    A class that represents a command message.
    """

    def __init__(self, user_email, ttl_hours, command_payload):
        self._pb = aerofs.command.gen.cmd_pb2.TransientCommandRequest()

        self._pb.user_email = user_email
        self._pb.ttl_hours = ttl_hours
        self._pb.command.command_id = 0 # Doesn't matter.
        self._pb.command.type = command_payload.get_type()

    def get_serialized_pb(self):
        return self._pb.SerializeToString()

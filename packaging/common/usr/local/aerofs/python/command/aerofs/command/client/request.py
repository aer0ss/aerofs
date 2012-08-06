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
    'CHECK_UPDATE'])

"""
A class that represents the payload of a command message.
"""
class CommandPayload(object):
    def __init__(self, type):
        # We require smart users! They should make sure the type they provide is indeed a valid
        # commands enum.
        assert type in Commands

        # Create the appropriate protobuf object depending on the type.
        if type == Commands.UPLOAD_DATABASE:
            self._type = aerofs.command.gen.cmd_pb2.UPLOAD_DATABASE
            self._pb = aerofs.command.gen.cmd_pb2.CommandUploadDatabase()
        elif type == Commands.CHECK_UPDATE:
            self._type = aerofs.command.gen.cmd_pb2.CHECK_UPDATE
            self._pb = aerofs.command.gen.cmd_pb2.CommandCheckUpdate()
        else:
            # Should never reach here because of the first assert, assuming all enums are handled.
            assert False, 'unhandled command type'

    def get_type(self):
        return self._type

    def get_payload(self):
        return self._pb.SerializeToString()

"""
A class that represents a command message.
"""
class CommandRequest(object):
    def __init__(self, user_email, ttl_hours, command_payload):
        self._pb = aerofs.command.gen.cmd_pb2.CommandRequest()

        self._pb.user_email = user_email
        self._pb.ttl_hours = ttl_hours
        self._pb.command.command_id = 0 # Doesn't matter.
        self._pb.command.type = command_payload.get_type()
        self._pb.command.payload = command_payload.get_payload()

    def get_serialized_pb(self):
        return self._pb.SerializeToString()

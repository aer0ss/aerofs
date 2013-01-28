"""
This class encapsulates all functionality related to the redis database. As is redis style,
we use key prefixes to differentiate different types of data in the db (meta vs. command entries).
"""

import redis
import aerofs.command.server.log
import aerofs.command.gen.cmd_pb2

class TransientCommandDatabaseException(Exception):
    def __init__(self, message):
        self._message = message
        Exception.__init__(self)
    def __str__(self):
        return self._message

class TransientCommandDatabase(object):
    # Separate different pieces of keys. Use double dot because they are not allowed in email
    # addresses.
    _separator = '..'

    # Prefix for keys that map to a serialized protobuf command.
    _prefix_command = 'cmdsrv-cmd'
    # Prefix meta data.
    _prefix_meta = 'cmdsrv-meta'

    # Key for the command id.
    _key_command_id = _separator.join([_prefix_meta, 'id'])

    def __init__(self, log_handler, log_level):
        self._l = aerofs.command.server.log.get_logger('cmdsrv-db', log_handler, log_level)
        self._redis = redis.Redis()

    # Get the user emails for all commands currently stored in redis. Use a set so we do not get
    # duplicates in the return.
    def get_user_emails_(self):
        keys = self._redis.keys(self._prefix_command + '*')
        result = set()

        for key in keys:
            prefix, email, command_id = key.split(self._separator)
            result.add(email)

        return result

    # Get a list of serialized commands for the given user email address.
    def get_command_bytes_(self, user_email):
        keys = self._redis.keys(self._separator.join([self._prefix_command, user_email, "*"]))

        command_bytes = []

        for key in keys:
            payload = self._redis.get(key)
            command_bytes.append(payload)

        return command_bytes

    # Add a new command the redis db.
    def add_(self, command_request):
        next_id = self._redis.get(self._key_command_id)

        if next_id is None:
            next_id = 1
        else:
            next_id = long(next_id)

        self._l.debug('Transient Command ID: ' + str(next_id))

        pipeline = self._redis.pipeline()
        pipeline.incr(self._key_command_id)

        command = command_request.command
        command.command_id = next_id

        # Don't go nuts here verifying the whole email address syntax, but at the very least make
        # sure that it doesn't contain a double dot, so that our separator doesn't break.
        assert command_request.user_email.find(self._separator) == -1, 'invalid user email'

        # Create the command entry. Include the command ID in the key.
        command_key = self._separator.join([self._prefix_command, command_request.user_email,
            str(next_id)])
        pipeline.set(command_key, command.SerializeToString())

        pipeline.expire(command_key, command_request.ttl_hours * 3600)
        result = pipeline.execute()

        # result holds [#commands, #newkeys, pass/fail boolean].
        if not result[2]:
            raise TransientCommandDatabaseException('Unable to complete redis transaction.')

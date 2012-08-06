"""
This script is for developers to send commands to the AeroFS Command Server.
"""

import sys
import getopt
import aerofs.command.client.request
import aerofs.command.client.http

def usage(error):
    print 'Error: ' + error
    print
    print 'Usage: cmd <switches>'
    print
    print 'Switches:'
    print ' -t <command_type> The type of command. Available commands:'

    for cmd in aerofs.command.client.request.Commands:
        print '                    -', cmd

    print ' -u <user_email>   The email address of the user that you would like to command.'
    print ' -h <ttl_hours>    The number of hours the command server will try to send your command to user devices.'
    print
    print 'Example: cmd -t UPLOAD_DATABASE -u matt@aerofs.com -h 24'

def main():
    # Parse command line options.
    try:
        opts, args = getopt.getopt(sys.argv[1:], 't:u:h:')
    except getopt.GetoptError, e:
        usage(str(e))
        sys.exit(1)

    command_type = ''
    user_email = ''
    ttl_hours = 0

    for o, a in opts:
        if o == '-t':
            if not a in aerofs.command.client.request.Commands:
                usage('unrecognized command: \"' + a + '\"')
                sys.exit(1)
            command_type = a
        elif o == '-u':
            user_email = a

            if len(user_email.split('@')) != 2:
                usage ('invalid email address: \"' + user_email + '\"')
                sys.exit(1)
        elif o == '-h':
            try:
                ttl_hours = int(a)
            except ValueError, e:
                usage('invalid integer: \"' + a + '\"')
                sys.exit(1)

            if ttl_hours < 1:
                usage('-h must be one or more hours')
                sys.exit(1)
        else:
            assert False, 'unhandled option' + o

    if len(command_type) == 0 or len(user_email) == 0 or ttl_hours <= 0:
        usage('all switches are required.')
        sys.exit(1)

    print 'Command details:'
    print ' >> command_type: ' + command_type
    print ' >> user_email: ' + user_email
    print ' >> ttl_hours: ' + str(ttl_hours)
    print 'Publishing command...'

    try:
        # Get the protobuf style payload that we will send across the wire.
        command_payload = aerofs.command.client.request.CommandPayload(command_type)
        command_message = aerofs.command.client.request.CommandRequest(
                user_email,
                ttl_hours,
                command_payload)
        post_data = command_message.get_serialized_pb()

        # Perform the actual HTTP opertation.
        http_cmd_request = aerofs.command.client.http.HttpCommandRequest('c.aerofs.com', 80)
        response = http_cmd_request.post(post_data)

        if response.status != 200:
            print "Error: server returned error code {0}.".format(response.status)
            sys.exit(1)

        print 'Done. Your command has been received by the redis command server.'

    except Exception as e:
        print 'Error: ' + str(e)

if __name__ == '__main__':
    main()

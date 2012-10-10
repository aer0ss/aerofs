import aerofs.command.server.web.common
import aerofs.command.server.log
import aerofs.command.server.db
import aerofs.command.gen.cmd_pb2

class RequestApplicationObject(object):
    """
    The request application object class is responsible for handling all command requests.
    """

    def __init__(self, log_handler, log_level, request_pusher):
        self._request_pusher = request_pusher

        self._l = aerofs.command.server.log.get_logger('cmdsrv-request', log_handler, log_level)
        self._command_db = aerofs.command.server.db.CommandDatabase(log_handler, log_level)

    def __call__(self, environ, response):
        # Read the post body.
        request_body_size = int(environ['CONTENT_LENGTH'])
        bytes = environ['wsgi.input'].read(request_body_size)

        command_request = aerofs.command.gen.cmd_pb2.CommandRequest()
        command_request.ParseFromString(bytes)

        self._l.info('Received cmd request for ' + command_request.user_email)

        # Add the command to the redis database and poke the pusher to send this command to
        # verkehr quickly.
        try:
            self._command_db.add_(command_request)
            self._request_pusher.poke_(command_request.user_email)
        except Exception as e:
            # Log the error and re-throw, for debugging purposes.
            self._l.error(str(e))
            raise e

        response('200 OK', [('content-type', 'text/plain')])
        return ''


class RequestServer(aerofs.command.server.web.common.CommandWebServer):
    """
    An object that represents a command request server.
    """

    def __init__(self, port, log_handler, log_level, request_pusher):
        # Create an application object.
        app_object = RequestApplicationObject(log_handler, log_level, request_pusher)

        aerofs.command.server.web.common.CommandWebServer.__init__(
            self,
            port,
            app_object,
            log_handler,
            log_level)

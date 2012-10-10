import threading
import wsgiref.simple_server
import aerofs.command.server.log

class CommandWebServer(threading.Thread):
    """
    A utility class (used for both the request and response WSGI servers). This class hooks nicely
    up to nginx on a specific port.

    The application object that you provide as a parameter must be callable and must accept two
    params: the environment and the WSGI response object.
    """

    def __init__(self, port, app_object, log_handler, log_level):

        # Create a logger
        self._l = aerofs.command.server.log.get_logger('cmdsrv-web', log_handler, log_level)

        # Make the WSGI server.
        self._httpd = wsgiref.simple_server.make_server('localhost', port, app_object)

        # Thread stuff.
        self._shutdown = False
        threading.Thread.__init__(self)

    def run(self):
        try:
            self._httpd.serve_forever()
        except Exception, e:
            # We'll get a socket exception on shutdown, so ignore it.
            if not self._shutdown:
                self._l.error('web exception: ' + str(e))

    def stop(self):
        self._shutdown = True

        # No better way to do this, yuck. Everything else about the WSGI simple server is great
        # though, so bit the bullet and deal with this.
        self._httpd.socket.close()
        self.join()
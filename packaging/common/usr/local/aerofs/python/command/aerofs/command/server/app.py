import threading
import logging
import aerofs.command.server.log
import aerofs.command.server.web.request
import aerofs.command.server.pusher

class CommandServerApplication(threading.Thread):
    """
    This class encapsulates the functionality related to the command server application. It is
    really just a wrapper for the other threads that are required by the application.
    """

    def __init__(self,
                 request_port,
                 log_file,
                 log_level,
                 verkehr_cmd_host,
                 verkehr_cmd_port,
                 pusher_resend_secs):
        threading.Thread.__init__(self)

        # Create a log handler.
        handler = logging.FileHandler(log_file)
        formatter = logging.Formatter('%(asctime)-6s %(name)s:%(levelname)s %(message)s')
        handler.setFormatter(formatter)

        # And create a logger for this class.
        self._l = aerofs.command.server.log.get_logger('cmdsrv', handler, log_level)

        # Create the threads that this application will use.
        self._request_pusher  = aerofs.command.server.pusher.RequestPusher(
            handler,
            log_level,
            verkehr_cmd_host,
            verkehr_cmd_port,
            pusher_resend_secs)

        self._request_server  = aerofs.command.server.web.request.RequestServer(
            request_port,
            handler,
            log_level,
            self._request_pusher)

    def run(self):
        # Start the other threads, then this thread exits.
        self._l.info("Starting services...")
        self._request_pusher.start()
        self._request_server.start()
        self._l.info("Services started.")

    def stop(self):
        # Stop all the threads (these calls perform joins as well).
        self._l.info("Shutting down...")
        self._request_server.stop()
        self._request_pusher.stop()
        self._l.info("Shutdown complete.")

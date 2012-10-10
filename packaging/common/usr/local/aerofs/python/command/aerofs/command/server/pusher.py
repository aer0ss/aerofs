import sys
import traceback
import threading
import multiprocessing
import time
import aerofs.command.server.log
import aerofs.command.server.verkehr
import aerofs.command.server.db

class RequestPusher(threading.Thread):
    """
    The request pusher thread is responsible for pushing commands to verkehr.
    """

    def __init__(self,
                 log_handler,
                 log_level,
                 verkehr_cmd_host,
                 verkehr_cmd_port,
                 resend_secs):

        self._resend_secs = resend_secs
        self._l = aerofs.command.server.log.get_logger('cmdsrv-pusher', log_handler, log_level)
        self._verkehr = aerofs.command.server.verkehr.CommandClient(
            verkehr_cmd_host,
            verkehr_cmd_port)
        self._command_db = aerofs.command.server.db.CommandDatabase(log_handler, log_level)
        self._poke_queue = multiprocessing.Queue()

        # Thread init.
        self._shutdown = False
        threading.Thread.__init__(self)

    # Poke the pusher, indicating that a new user email has been added to redis. The pusher
    # should push this command out to verkehr immediately.
    def poke_(self, user_email):
        self._l.debug('Poked to send command for ' + user_email)
        self._poke_queue.put(user_email)

    def run(self):
        # Initialize this variable such that we send the first time through the loop.
        next_run = time.time()-1

        while not self._shutdown:
            try:
                if next_run < time.time():
                    next_run = time.time() + self._resend_secs

                    # Clear out the poke queue (we're about to send everything anyway).
                    while not self._poke_queue.empty():
                        self._poke_queue.get()

                    self._send_everything_()

                while not self._poke_queue.empty():
                    user_email = self._poke_queue.get()
                    self._send_one_(user_email)

            except Exception as e:
                exc_type, exc_value, exc_traceback = sys.exc_info()
                self._l.error(str(e))
                self._l.error(traceback.format_tb(exc_traceback))

            time.sleep(1)

    def _send_everything_(self):
        user_emails = self._command_db.get_user_emails_()

        for user_email in user_emails:
            self._send_one_(user_email)

    def _send_one_(self, user_email):
        command_bytes = self._command_db.get_command_bytes_(user_email)

        if len(command_bytes) > 0:
            self._l.debug('Send command for ' + user_email + ' cmds: ' + str(len(command_bytes)))

            result = self._verkehr.send(user_email, command_bytes)
            if not result:
                # This will be caught by the catch in the run method.
                raise Exception('unable to send command to verkehr')

            self._l.debug('Send successful')

    def stop(self):
        self._shutdown = True
        self.join()

"""
AeroFS Command Server -- Main Script.

This script is used to start the AeroFS command server. The command server is used to execute
commands on client systems via Verkehr.
"""

import sys
import signal
import threading
import logging
import ConfigParser
import aerofs.command.server.app

# Signal handling
signal_event = threading.Event()

def receive_shutdown(signum, stack):
    global signal_event
    signal_event.set()

# In case we are running from the command line.
signal.signal(signal.SIGINT, receive_shutdown)
# Standard service stop signal.
signal.signal(signal.SIGTERM, receive_shutdown)

# Main function.
def main():

    if len(sys.argv) != 2:
        print 'Usage: ' + sys.argv[0] + ' <cmdsrv_ini>'
        sys.exit(1)

    config = ConfigParser.RawConfigParser()
    config.read(sys.argv[1])

    if int(config.get('cmdsrv', 'verbose')) == 1:
        log_level = logging.DEBUG
    else:
        log_level = logging.WARN

    cmdsrv = aerofs.command.server.app.CommandServerApplication(
            int(config.get('cmdsrv', 'request_port')),
            config.get('cmdsrv', 'logfile'),
            log_level,
            config.get('cmdsrv', 'verkehr_host'),
            int(config.get('cmdsrv', 'verkehr_port')),
            int(config.get('cmdsrv', 'pusher_resend_interval')))

    # Run forever, or until we get a signal.
    cmdsrv.start()
    while True:
        signal_event.wait(10)
        if signal_event.isSet():
            cmdsrv.stop()
            break

if __name__ == "__main__":
    main()

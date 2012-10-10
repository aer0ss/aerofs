"""
This application runs on the admin panel. It allows the VM hosts to connect to
us so we can configure them and/or request status information.
"""

import sys
import signal
import threading
import logging
import inception.app.cmdline
import inception.app.constants
import inception.admin.manager

# Global event object used to signal the main thread.
signal_event = threading.Event()

def receive_shutdown(signum, stack):
    global signal_event
    signal_event.set()

# And the standard INT (interrupt) signal will be used for shutdown.
signal.signal(signal.SIGINT, receive_shutdown)

def main():
    logger = inception.app.cmdline.server_parseopts('VmHostsManager',
            inception.app.constants.LOG_FILE)

    logger.info('Starting application.')

    # Create an instance of the VM host Manager.
    manager = inception.admin.manager.VmHostsManager(logger,
            inception.app.constants.VMHOSTS_CLT_PORT,
            inception.app.constants.VMHOSTS_SRV_PORT,
            inception.app.constants.CERT_KEY_FILE)

    # Run forever... (or until we get a shutdown signal).
    manager.start()
    while True:
        signal_event.wait(10)
        if (signal_event.isSet()):
            manager.shutdown()
            break

    logger.info('Application shutdown complete.')

if __name__ == "__main__":
    main()

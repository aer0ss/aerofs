"""
This application runs on the VM host. It's primary purpose is to manage the
KVMs.

KVM management can be broken down into the following tasks:

    1. Configure KVMs (start/stop KVMs).
    2. Convey information about KVMs (state, etc.).

The main functionality of this application is implemented in the KvmsManager
class.
"""

import sys
import signal
import threading
import logging
import inception.app.cmdline
import inception.app.constants
import inception.vmhost.manager

# Global event object used to signal the main thread.
signal_event = threading.Event()

def receive_shutdown(signum, stack):
    global signal_event
    signal_event.set()

# And the standard INT (interrupt) signal will be used for shutdown.
signal.signal(signal.SIGINT, receive_shutdown)

def main():
    logger = inception.app.cmdline.server_parseopts('KvmsManager',
            inception.app.constants.LOG_FILE)

    logger.info('Starting application.')

    # Create an instance of the VM host Manager.
    manager = inception.vmhost.manager.KvmsManager(logger,
            inception.app.constants.KVMS_CLT_PORT,
            inception.app.constants.KVMS_SRV_PORT,
            inception.app.constants.VMHOSTS_SRV_PORT,
            inception.app.constants.ADMIN_ADDR_FILE,
            inception.app.constants.VMHOST_ID_FILE,
            inception.app.constants.AUTOSTART_DIR,
            inception.app.constants.KVM_IMG_DIR,
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

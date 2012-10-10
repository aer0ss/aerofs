"""
This application runs on every KVM and is responsible for connecting to the
appropriate VM host and communicating KVM health and related information back up
to the VM host.
"""

import signal
import threading
import inception.app.cmdline
import inception.kvm.manager
import inception.app.constants

# Global event object used to signal the main thread.
signal_event = threading.Event()

def receive_shutdown(signum, stack):
    global signal_event
    signal_event.set()

signal.signal(signal.SIGINT, receive_shutdown)

def main():
    logger = inception.app.cmdline.server_parseopts('ServicesManager',
            inception.app.constants.LOG_FILE)
    logger.info('Starting application.')

    # Create an instance of our manager.
    manager = inception.kvm.manager.ServicesManager(logger,
            inception.app.constants.KVMS_SRV_PORT,
            inception.app.constants.VMHOST_ADDR_FILE,
            inception.app.constants.KVM_SERVICE_FILE,
            inception.app.constants.CERT_KEY_FILE)

    # Run forever...
    manager.start()

    while True:
        signal_event.wait(10)
        if signal_event.isSet():
            manager.shutdown()
            break

if __name__ == "__main__":
    main()

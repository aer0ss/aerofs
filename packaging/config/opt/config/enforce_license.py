#!/usr/bin/env python
# A script which is run periodically to see if the current license file has expired.
# If the license file doesn't exist or is not currently valid, then it shuts
# down SP, verkehr, zephyr, and ejabberd.

from aerofs_licensing import license_file
import os
import subprocess
import sys

LICENSE_FILE_PATH = "/etc/aerofs/license.gpg"
SERVICES_TO_STOP = ["tomcat6", "verkehr", "zephyr"]

def shut_down_services_and_exit():
    for service in SERVICES_TO_STOP:
        print "stopping", service
        shut_down_service(service)
    print "All services stopped."
    sys.exit(0)

def shut_down_service(service):
    subprocess.call(["service", service, "stop"])

if __name__ == "__main__":
    if not os.path.exists(LICENSE_FILE_PATH):
        print "No license file available, stopping services"
        shut_down_services_and_exit()
    else:
        try:
            license_handle = open(LICENSE_FILE_PATH)
            license = license_file.verify_and_load(license_handle)
        except:
            print "License file did not validate, stopping services"
            shut_down_services_and_exit()
        if not license.is_currently_valid():
            print "License file has expired, stopping services"
            shut_down_services_and_exit()
    # If we get to here, the license is valid.  Do nothing.

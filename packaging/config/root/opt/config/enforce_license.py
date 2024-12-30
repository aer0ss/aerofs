#!/usr/bin/env python
# A script which is run periodically to see if the current license file has expired.
# If the license file doesn't exist or is not currently valid, then it shuts
# down lipwig and zephyr.

from aerofs_licensing import license_file
import os
import subprocess
import sys

LICENSE_FILE_PATH = "/etc/aerofs/license.gpg"
SERVICES_TO_STOP = ["lipwig", "zephyr"]

def shut_down_services_and_create_flag_file_and_exit():
    for service in SERVICES_TO_STOP:
        print(("stopping", service))
        shut_down_service(service)
    print("all services stopped")
    create_license_expired_flag_file()
    sys.exit(0)

def shut_down_service(service):
    subprocess.call(["service", service, "stop"])

def create_license_expired_flag_file():
    """
    Create the license-expire flag file, which is used by nginx to redirect Web access to the
    license-already-expired page.
    """
    parent = os.path.join(os.sep, 'var', 'aerofs')
    try:
        if not os.path.exists(parent): os.makedirs(parent)
        # Create an empty file
        open(os.path.join(parent, 'license-expired-flag'), 'w').close()
    except Exception as e:
        # Best effort. move on if error occurs
        print(("ignore error:", e))

if __name__ == "__main__":
    if not os.path.exists(LICENSE_FILE_PATH):
        print("No license file available, stopping services")
        shut_down_services_and_create_flag_file_and_exit()
    else:
        try:
            license_handle = open(LICENSE_FILE_PATH)
            license = license_file.verify_and_load(license_handle)
        except:
            print("License file did not validate, stopping services")
            shut_down_services_and_create_flag_file_and_exit()
        if not license.is_currently_valid():
            print("License file has expired, stopping services")
            shut_down_services_and_create_flag_file_and_exit()
    # If we get to here, the license is valid.  Do nothing.

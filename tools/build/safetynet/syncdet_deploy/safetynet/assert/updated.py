import os
import urllib2
import time

from lib import app
from safetynet import client

def monitor_version_file():
    """Gets the current version number of AeroFS and waits until the client
    has downloaded and installed it, signified by the version file in approot
    being updated.

    """
    data = urllib2.urlopen(client.instance().current_version_url())
    new_release_version = data.readline().replace("Version=", "").strip()

    line = ''
    while line != new_release_version:
        try:
            # Sleeping right away is fine because clients are known to
            # actually update well after this test case is started
            time.sleep(1)

            # We need to get the file path each time because the path
            # changes between updates on Windows
            version_file_path = client.instance().version_file_path()

            # Check if the version file exists. We do not use lib.files.wait_file
            # because we need to grab a new version file path each time
            if not os.path.exists(version_file_path):
                continue

            with open(version_file_path, 'r') as f:
                line = f.readline().strip()
        except IOError:
            pass

spec = { 'default': monitor_version_file, 'timeout': 180 }

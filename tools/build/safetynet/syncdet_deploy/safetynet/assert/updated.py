import os
import urllib2
import time

from lib import app
from lib.files import wait_file
from safetynet import client

def monitor_version_file():
    """Gets the current version number of AeroFS and waits until the client
    has downloaded and installed it, signified by the version file in approot
    being updated.

    """
    data = urllib2.urlopen(client.instance().current_version_url())
    new_release_version = data.readline().replace("Version=", "").strip()

    version_file_path = client.instance().version_file_path()

    line = ''
    while line != new_release_version:
        try:
            wait_file(version_file_path)
            time.sleep(1)
            with open(version_file_path, 'r') as f:
                line = f.readline().strip()
        except IOError:
            pass

spec = { 'default': monitor_version_file, 'timeout': 180 }

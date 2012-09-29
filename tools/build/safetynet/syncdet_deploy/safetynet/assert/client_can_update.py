import os
import urllib2

from lib import app
from safetynet import client

def assert_client_can_update():
    """Check that the current AeroFS release version is not the same
    as the remote actor's AeroFS release version.

    """
    url = client.instance().current_version_url()

    # Don't need to close this (it doesn't even have an __exit__ method for use with 'with')
    current_version = urllib2.urlopen(url).readline().replace("Version=", "").strip()

    with open(client.instance().version_file_path(), 'r') as f:
        local_version = f.readline().strip()
        if local_version == current_version:
            raise Exception("local client has same version as current SafetyNet release. Won't update.")

spec = { 'default': assert_client_can_update }

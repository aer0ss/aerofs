import os
import re
import tempfile

from lib import app
from safetynet import client

def extract_version_components(string):
    match_result = re.match(r"(\d+)\.(\d+)\.(\d+)", string)
    if not match_result:
        raise Exception("no version found in {}".format(string))

    return (int(group) for group in match_result.groups())

def decrease_version():
    if client.instance().is_aerofs_running():
        raise Exception("can not decrease version: AeroFS is still running")

    version_path = client.instance().version_file_path()

    data = ""
    major, minor, build = (0, 0, 0)

    # Read the version number from the file
    with open(version_path, 'r') as f:
        # Extract the version number in pieces
        major, minor, build = extract_version_components(f.readline())

        # Decrement the build number
        build -= 1
        if build < 0:
            raise Exception("can't decrease build version below zero")

        # Preserve the rest of the data in the file. This includes
        # checksums of the AeroFS binaries.
        data = f.read()

    # Write the new version file to a temporary location
    temp_file_descriptor, abs_path = tempfile.mkstemp()
    with os.fdopen(temp_file_descriptor, 'w') as temp_version_file:
        temp_version_file.write("{0}.{1}.{2}\n".format(major, minor, build))
        temp_version_file.write(data)

    # Replace the version file with the newly decremented one
    os.rename(abs_path, version_path)

spec = { 'default': decrease_version }

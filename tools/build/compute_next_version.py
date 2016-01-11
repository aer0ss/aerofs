#!/usr/bin/env python
#
# Compute and print the next release version to use.
#

import requests
from distutils.version import StrictVersion
from os.path import dirname, realpath
import sys


def compute_next_version(image):
    # Get all tags for that image
    r = requests.get("https://registry.aerofs.com/v1/repositories/aerofs/{}/tags".format(image))
    r.raise_for_status()
    tags = r.json()

    # Create a list of version numbers, ignore tags that do not represent a valid version number in the form x.y.z
    versions = []
    for tag in tags.iterkeys():
        try:
            versions.append(StrictVersion(tag))
        except ValueError:
            pass

    if len(versions) == 0:
        raise Exception("No version numbers where found for image aerofs/{}".format(image))

    versions.sort()
    current_version = versions[-1]

    # Read the 'version' file located in the same directory as this script.
    # This file contains the major.minor version that we want to use.
    with open("{}/version".format(pwd()), "r") as f:
        next_major_minor = StrictVersion(f.read())
        if next_major_minor > current_version:
            current_version = next_major_minor

    # Finally, print out the next version number
    major, minor, build = current_version.version
    return "{}.{}.{}".format(major, minor, build+1)


def pwd():
    # returns the directory of this script
    return dirname(realpath(__file__))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print """Prints the next version number for a container.

Usage:
    {} <image>

<image> is the name of one of our Docker images on registry.aerofs.com for which we want to compute
the next version number.

Note: this tool just bumps the build number. To change the major or minor number, update the file
named 'version' in the same directory as this tool.
        """.format(sys.argv[0])

        exit(1)

    print compute_next_version(sys.argv[1])

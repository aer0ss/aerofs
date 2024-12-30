#!/usr/bin/env python3
#
# Compute and print the next release version to use.
#

import re
import subprocess
from os.path import dirname, realpath


version_re = re.compile(r'^([0-9]+)\.([0-9]+)(?:\.([0-9]+))?$', re.ASCII)


def strict_version(vstr):
    m = version_re.match(vstr)
    if not m:
        return None
    g = m.groups()
    if not g[2]:
        g = (g[0], g[1], 0)
    return tuple(map(int, g)) if m else None


def compute_next_version():
    # Get all tags for that image
    tags = subprocess.check_output(['git', 'tag', '-l', 'docker-*']).decode('utf-8').split('\n')

    # Create a list of version numbers, ignore tags that do not represent a valid version number in the form x.y.z
    versions = []
    for tag in tags:
        v = strict_version(tag[len('docker-'):])
        if v:
            versions.append(v)

    if len(versions) == 0:
        raise Exception("No version numbers were found in git tags")

    current_version = max(versions)

    # Read the 'version' file located in the same directory as this script.
    # This file contains the major.minor version that we want to use.
    with open("{}/version".format(pwd()), "r") as f:
        next_major_minor = strict_version(f.read())
        if next_major_minor > current_version:
            current_version = next_major_minor

    # Finally, print out the next version number
    major, minor, build = current_version
    return "{}.{}.{}".format(major, minor, build+1)


def pwd():
    # returns the directory of this script
    return dirname(realpath(__file__))


if __name__ == '__main__':
    print(compute_next_version())

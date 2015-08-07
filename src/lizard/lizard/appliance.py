import os

import requests
from flask import current_app

# Urls for the current appliance images.
_BUCKET_BASE = "http://d1sj1ujwunpr12.cloudfront.net"

def ova_url(version):
    return "{}/aerofs-appliance-{}.ova".format(_BUCKET_BASE, version)

def qcow_url(version):
    return "{}/aerofs-appliance-{}.qcow2.gz".format(_BUCKET_BASE, version)

def vhd_url(version):
    return "{}/aerofs-appliance-{}.vhd".format(_BUCKET_BASE, version)

# A URL where a base release version number is stored on S3.  Only used if no
# other version number is known; otherwise, the app tracks what it believes to
# be the latest appliance version in the state directory.
def _release_version_url():
    return "https://s3.amazonaws.com/aerofs.privatecloud/release-version"

def latest_appliance_version():
    filename = current_app.config["RELEASE_VERSION_FILE"]
    if not os.path.exists(filename):
        r = requests.get(_release_version_url())
        r.raise_for_status()
        version = r.text.strip()
        with open(filename, "wb") as f:
            f.write(version.encode("utf-8") + '\n')
    with open(filename) as f:
        return f.read().strip()

def set_public_version(version):
    filename = current_app.config["RELEASE_VERSION_FILE"]
    with open(filename, "w") as f:
        f.write(version.encode("utf-8") + '\n')

def ova_present_for(version):
    ova = ova_url(version)
    r = requests.head(ova)
    return r.ok

def qcow_present_for(version):
    qcow = qcow_url(version)
    r = requests.head(qcow)
    return r.ok

def vhd_present_for(version):
    qcow = vhd_url(version)
    r = requests.head(qcow)
    return r.ok

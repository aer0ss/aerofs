import logging
import os
import time

import requests

_private_version = None
_public_version = None
_public_last_refresh = 0

log = logging.getLogger(__name__)


def get_private_version(settings):
    """
    Return the current AeroFS version string. This function reads the current.ver in the local
    filesystem which is available only in private deployment.

    Note: enterprise-greeter duplicates this function.
    """
    global _private_version
    # cache the value to avoid frequent reads
    if not _private_version:
        path = os.path.join(settings.get('deployment.installer_folder', "/opt/repackaging/installers/original"), "current.ver")
        with open(path) as f:
            _private_version = _parse_version(f.readline())

    return _private_version


def get_public_version(settings):
    """
    Return the current AeroFS version string. This function reads the current.ver from the installer
    URL. Private deployment can't use this method because the URL is usually inaccessible from the
    appliance itself.

    To avoid frequent network access, this method cache the version in memory and refresh it once
    every minute.
    """
    global _public_version, _public_last_refresh

    if time.time() - _public_last_refresh > 60:
        r = requests.get(settings['installer.prefix'] + '/current.ver')
        if r.ok:
            _public_version = _parse_version(r.text)
            _public_last_refresh = time.time()
        elif _public_version:
            ## Use the old value if there is any
            log.warn("can't read current.ver. status code {}. use old value".format(
                r.status_code))
        else:
            raise requests.RequestException("can't read current.ver. status code {}".format(
                r.status_code))

    return _public_version


def _parse_version(string):
    """
    Given a string in the form of "Version=1.2.3" (leading & trailing whitespace allowed), return
    the version string ("1.2.3")
    """
    _, version = string.strip().split("=", 1)
    return version

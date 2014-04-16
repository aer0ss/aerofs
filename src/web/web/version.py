import logging
import time
import requests

_private_version = None
_public_version = None
_public_last_refresh = 0

log = logging.getLogger(__name__)


def get_private_version():
    """
    Return the current AeroFS version string. This function reads the current.ver in the local
    filesystem which is available only in private deployment.

    Note: enterprise-greeter duplicates this function.
    """
    global _private_version
    # cache the value to avoid frequent reads
    if not _private_version:
        with open("/opt/repackaging/installers/original/current.ver") as f:
            version_line = f.readline().rstrip()
            _, _private_version = version_line.split("=", 1)

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
            _, _public_version = r.text.strip().split("=", 1)
            _public_last_refresh = time.time()
        elif _public_version:
            ## Use the old value if there is any
            log.warn("can't read current.ver. status code {}. use old value".format(
                r.status_code))
        else:
            raise requests.RequestException("can't read current.ver. status code {}".format(
                r.status_code))

    return _public_version

import logging
import os

_private_version = None

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


def _parse_version(string):
    """
    Given a string in the form of "Version=1.2.3" (leading & trailing whitespace allowed), return
    the version string ("1.2.3")
    """
    _, version = string.strip().split("=", 1)
    return version

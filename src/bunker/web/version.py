#!/usr/bin/env python
_version = None

def get_current_version():
    """
    Return the current AeroFS version as a string.

    The canonical source of the release version is the current.ver file
    provided with the client installers.

    (Note that enterprise-greeter duplicates this function.)
    """
    global _version
    # cache the version to avoid frequent reads
    if not _version:
        try:
            with open("/opt/repackaging/installers/original/current.ver") as f:
                version_line = f.readline().rstrip()
                _, _version = version_line.split("=", 1)
        except:
            _version = "(unknown)"

    return _version

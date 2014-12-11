import os.path

from aerofs_ritual.gen.common_pb2 import PBPath
from aerofs_ritual.id import get_root_sid_bytes


def store_relative_to_pbpath(sid, relative):
    components = []
    while True:
        (relative,tail) = os.path.split(relative)
        if tail == "": break
        components.append(tail)

    pbpath = PBPath()
    components.reverse()
    pbpath.sid = sid
    pbpath.elem.extend(components)
    return pbpath


def _string_to_pbpath(path, user=None):
    if user is None:
        from lib.app.cfg import get_cfg
        user = get_cfg().user()
    return store_relative_to_pbpath(get_root_sid_bytes(user), path)


def _absolute_to_aerofs_path(absolute, root_anchor=None):
    """Given an absolute path, return a string representing the path relative to the AeroFS root"""
    if root_anchor is None:
        from lib.app.cfg import get_cfg
        root_anchor = get_cfg().get_root_anchor()
    if root_anchor not in absolute:
        raise ValueError("absolute path must contain root anchor")
    # Cannot use os.path.relpath on win python for paths longer than 260 chars and some
    # other non-windows friendly paths.
    # Cannot add magic prefix "\\\\?\\" also because magic prefix only works with
    # absolute paths.
    return absolute[len(root_anchor):]

def absolute_to_pbpath(absolute, root_anchor=None):
    return _string_to_pbpath(_absolute_to_aerofs_path(absolute, root_anchor))


def pbpath_to_absolute(pbpath, root_anchor=None):
    if root_anchor is None:
        from lib.app.cfg import get_cfg
        root_anchor = get_cfg().get_root_anchor()
    return os.path.join(root_anchor, *pbpath.elem)

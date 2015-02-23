"""
Wipe rtroot and reinstall a TS
Expects all roots to be re-joined correctly and sync to work after reinstall

The first actor MUST be a Team Server configured to use LINKED storage
"""

import os
from lib.app.cfg import get_cfg
from lib.app import aerofs_proc

import should_restore_shared_folders_on_ts_reinstall


def ts_unclean_uninstall():
    aerofs_proc.stop_all()
    # remove conf db to force reinstall
    os.remove(os.path.join(get_cfg().get_rtroot(), "conf"))


def ts_unclean_validate(r0, r1):
    # ignore roots that cannot be automatically rejoined after unclean reinstall
    root = get_cfg().get_root_anchor()
    return all((r1.get(s) == p or not p.startswith(root)) for s, p in r0.items())

spec = should_restore_shared_folders_on_ts_reinstall.spec(ts_unclean_uninstall, ts_unclean_validate)
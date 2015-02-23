"""
Unlink and reinstall a TS
Expects all roots to be re-joined correctly and sync to work after reinstall

The first actor MUST be a Team Server configured to use LINKED storage
"""

from aerofs_sp import sp
from lib.app.cfg import get_cfg
from lib.app import aerofs_proc

import should_restore_shared_folders_on_ts_reinstall


def ts_clean_uninstall():
    s = sp.connect()
    s.sign_in()
    s.unlink(get_cfg().did().get_bytes())

    # UI will kill daemon and exit upon reception of an unlink command
    aerofs_proc.wait_for_all_to_die()


def ts_clean_validate(r0, r1):
    return all(r1.get(s) == p for s, p in r0.items())

spec = should_restore_shared_folders_on_ts_reinstall.spec(ts_clean_uninstall, ts_clean_validate)

# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a team server configured to use linked storage
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization


from . import relocate
from core.multiroot.teamserver import ts_user_sid_bytes, ts_user_root
from lib.files import wait_dir


def teamserver():
    sid = ts_user_sid_bytes()
    root = ts_user_root()

    # wait for the user folder to appear on the FS before trying to relocate it
    wait_dir(root)

    relocate(root + "_moved", sid)
    relocate(root, sid)


def client():
    pass

spec = {'entries': [teamserver, client]}

# IMPORTANT - Conditions for running tests using these utilities:
#   -   The first actor in the config file needs to be a team server configured to use linked storage
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization

from lib.app.cfg import get_cfg
from lib.app import aerofs_proc
from syncdet.case import instance_unique_string
from syncdet.actors import actor_list
from aerofs_common import exception
from aerofs_sp import sp as sp_service
from lib import ritual
from aerofs_ritual.id import get_root_sid_bytes
from syncdet.case.assertion import fail, expect_exception

import socket

def relocate(root, sid):
    r = ritual.connect()
    expect_exception(r.relocate, socket.error)(root, sid)
    aerofs_proc.stop_all()
    aerofs_proc.run_ui()


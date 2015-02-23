from aerofs_ritual import ritual
from aerofs_ritual.ritual import OWNER, EDITOR
from lib.app.cfg import get_cfg


def connect(max_attempts=None):
    socket_file = get_cfg().get_ritual_path()
    if max_attempts is None:
        return ritual.connect(socket_file)
    else:
        return ritual.connect(socket_file, max_attempts)


def wait_for_heartbeat(max_attempts=None):
    if max_attempts is None:
        connect().wait_for_heartbeat()
    else:
        connect().wait_for_heartbeat(max_attempts)

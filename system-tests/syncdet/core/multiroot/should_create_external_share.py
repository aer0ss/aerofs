import binascii
import os
import sys
import time

from syncdet.case import instance_unique_string
from syncdet.case.sync import sync

from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from lib.app.cfg import get_cfg
from lib.files.files import wait_file_with_content


FILENAME = "hello"
CONTENT = "world"

def sharer():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    os.mkdir(path)

    with open(os.path.join(path, FILENAME), 'w') as f:
        f.write(CONTENT)

    sid = ritual.connect().create_root(path)
    print path, binascii.hexlify(sid)

    sync("shared")


def sharee():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    sync("shared")

    sid = None
    r = ritual.connect()
    while True:
        for root in r.list_unlinked_roots():
            if root.name == instance_unique_string():
                sid = root.sid
                break
        if sid is not None:
            break
        time.sleep(POLLING_INTERVAL)

    print path, binascii.hexlify(sid)

    os.mkdir(path)
    r.link_root(path, sid)

    wait_file_with_content(os.path.join(path, FILENAME), CONTENT)


spec = { "entries": [sharer], "default": sharee }

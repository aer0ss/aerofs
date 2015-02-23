"""
This test is to check that we only can link unlinked roots.
The sharer creates a store. In the sharee , that store is initially
an unlinked root. We make sure that we can link it in the sharee.
We also try to link the same store in the sharer but since its already
linked we expect to see an exception.
"""
import binascii
import os
import sys
import time

from syncdet.case import instance_unique_string
from syncdet.case.sync import sync

from aerofs_common.exception import ExceptionReply
from aerofs_ritual.gen.common_pb2 import PBException
from aerofs_common.param import POLLING_INTERVAL
from syncdet.case.assertion import assertTrue
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
    # Assert that if we try to link an already linked folder, we throw
    try:
        sid = ritual.connect().link_root(path_for_ritual, sid)
    except ExceptionReply as e:
        assertTrue(e.get_type() == PBException.CHILD_ALREADY_SHARED)

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


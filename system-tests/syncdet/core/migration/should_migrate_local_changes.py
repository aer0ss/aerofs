"""
Migrate a folder into a shared folder
Ensure local changes are not lost in translation
"""

import os
import shutil
from lib.files import instance_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from lib import ritual
from lib.network_partition import GlobalNetworkPartition
from syncdet.case.sync import sync

FILENAME = 'foo'
CONTENT = 'bar'

BASE = {
    "foo": {},
    "bar": {
        "baz": "hello"
    }
}

END = {
    "foo": {
        ".aerofs": '',
        "moved": {
            "baz": "world",
            "qux": {
                "quux": "???"
            }
        }
    }
}

def creator():
    print 'creator'

    InstanceUniqueDirTree(BASE).write()

    sync("synced")
    r = ritual.connect()
    r.share_folder(instance_path("foo"))

    sync("shared")

    with GlobalNetworkPartition():
        os.makedirs(instance_path("bar", "qux"))
        with open(instance_path("bar", "baz"), "w") as f:
            f.write("world")
        with open(instance_path("bar", "qux", "quux"), "w") as f:
            f.write("???")

    final_state()


def syncer():
    print 'syncer'

    wait_for_any(InstanceUniqueDirTree(BASE))

    sync("synced")

    r = ritual.connect()
    r.wait_shared(instance_path("foo"))

    sync("shared")

    with GlobalNetworkPartition():
        shutil.move(instance_path("bar"), instance_path("foo", "moved"))

    final_state()


def final_state():
    wait_for_any(InstanceUniqueDirTree(END, ignore_content=[".aerofs"], ignore_file=["desktop.ini"]))


spec = {'entries': [creator], 'default': syncer}

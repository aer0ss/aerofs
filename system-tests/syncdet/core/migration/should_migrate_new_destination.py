"""
ENG-3805: Migrate an object known to polaris into one not yet know
"""

import os
import shutil
from lib.app import aerofs_proc
from lib.files import instance_path, wait_file_with_content
from lib.files.dirtree import InstanceUniqueDirTree
from lib import ritual
from syncdet.case.sync import sync

CONTENT = 'baz'


def creator():
    print 'creator'

    r = ritual.connect()
    InstanceUniqueDirTree({'foo': {"qux": {"quux": CONTENT}}}).write()
    sync(1)
    r.share_folder(instance_path('foo'))

    sync(2)

    aerofs_proc.stop_all()

    os.makedirs(instance_path("bar"))
    shutil.move(instance_path("foo", "qux"), instance_path("bar", "qux"))

    aerofs_proc.run_ui()

    r = ritual.connect()
    r.wait_path(instance_path("bar", "qux"))


def observer():
    print 'syncer'

    wait_file_with_content(instance_path("foo", "qux", "quux"), CONTENT)
    sync(1)
    r = ritual.connect()
    r.wait_shared(instance_path('foo'))

    sync(2)

    wait_file_with_content(instance_path("bar", "qux", "quux"), CONTENT)


spec = {'entries': [creator], 'default': observer}

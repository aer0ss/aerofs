"""
Migrate a file from a shared folder to the root and back
"""

import os
import shutil
from lib.files import instance_path, wait_path_to_disappear, wait_file_with_content
from lib.files.dirtree import InstanceUniqueDirTree
from lib import ritual
from syncdet.case.sync import sync

FILENAME = 'foo'
CONTENT = 'bar'


def original():
    return instance_path(FILENAME)


def migrated():
    return os.path.join(os.path.dirname(instance_path()), FILENAME)


def creator():
    print 'creator'

    r = ritual.connect()
    InstanceUniqueDirTree({FILENAME: CONTENT}).write()
    r.share_folder(instance_path())

    sync(1)

    shutil.move(original(), migrated())

    sync(2)

    shutil.move(migrated(), original())

    sync(3)

    # race conditions can cause a transient deadlock in the dependency graph
    # TODO: prevent crash and uncomment heartbeat check when fixed
    #r.heartbeat()


def syncer():
    print 'syncer'

    r = ritual.connect()
    wait_file_with_content(original(), CONTENT)
    r.wait_shared(instance_path())

    sync(1)

    wait_path_to_disappear(original())
    wait_file_with_content(migrated(), CONTENT)

    sync(2)

    wait_path_to_disappear(migrated())
    wait_file_with_content(original(), CONTENT)

    sync(3)

    # race conditions can cause a transient deadlock in the dependency graph
    # TODO: prevent crash and uncomment heartbeat check when fixed
    #r.heartbeat()


spec = {'entries': [creator], 'default': syncer}


import os
import shutil
from syncdet.case import sync
from lib.files import instance_path, wait_file_with_content, wait_dir

_FILE_CONTENT = "hello mr. shark"


def move():
    print 'mover'

    os.makedirs(instance_path("foo"))
    os.makedirs(instance_path("bar"))

    with open(instance_path("foo", "baz"), 'w') as f:
        f.write(_FILE_CONTENT)

    sync.sync("created")

    shutil.move(instance_path("foo", "baz"), instance_path("bar", "baz"))

    sync.sync("moved ro src")

    shutil.move(instance_path("bar", "baz"), instance_path("foo", "baz"))

    sync.sync("moved ro dst")

    shutil.move(instance_path("foo", "baz"), instance_path("bar", "baz"))

    sync.sync("moved ro src+dst")

    shutil.move(instance_path("bar", "baz"), instance_path("foo", "baz"))


def get():
    print 'syncer'

    wait_dir(instance_path("bar"))
    wait_file_with_content(instance_path("foo", "baz"), _FILE_CONTENT)

    # make source file read-only
    os.chmod(instance_path("foo", "baz"), 0400)

    sync.sync("created")

    wait_file_with_content(instance_path("bar", "baz"), _FILE_CONTENT)

    # make destination folder read-only
    os.chmod(instance_path("foo"), 0500)

    sync.sync("moved ro src")

    wait_file_with_content(instance_path("foo", "baz"), _FILE_CONTENT)

    # make *both* source and dest read-only
    os.chmod(instance_path("foo", "baz"), 0400)
    os.chmod(instance_path("bar"), 0500)

    sync.sync("moved ro dst")

    wait_file_with_content(instance_path("bar", "baz"), _FILE_CONTENT)

    # read-only *ALLTHETHINGS*
    os.chmod(instance_path("bar", "baz"), 0400)
    os.chmod(instance_path("bar"), 0500)
    os.chmod(instance_path("foo"), 0500)

    sync.sync("moved ro src+dst")

    wait_file_with_content(instance_path("foo", "baz"), _FILE_CONTENT)

spec = {"entries": [get, move], "default": get}
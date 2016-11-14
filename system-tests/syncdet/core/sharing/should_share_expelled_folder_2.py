"""
SUPPORT-2245
"""
import os
from lib import ritual
from lib.files import wait_dir, instance_path, wait_file_with_content
from syncdet.case.sync import sync
from syncdet.case.assertion import assertTrue


def sharer():
    path = instance_path('shared')
    os.makedirs(path)
    sync("synced")
    sync("expelled")
    # share the folder
    r = ritual.connect()
    r.share_folder(path)
    sync("share")
    r.wait_shared(path)
    sync("shared")

    # test sync in root folder *after* SHARE transform processed
    with open(instance_path("foo"), 'w') as f:
        f.write('bar')


def sharee():
    path = instance_path('shared')
    wait_dir(path)
    sync("synced")
    # expel the folder
    r = ritual.connect()
    r.exclude_folder(path)
    assertTrue(not os.path.exists(path))
    sync("expelled")
    sync("share")
    r.wait_shared(path)
    sync("shared")

    # test file sync in root folder
    # it can only succeed if the SHARE transform was processed
    wait_file_with_content(instance_path('foo'), 'bar')


spec = { "entries": [sharer], "default": sharee }

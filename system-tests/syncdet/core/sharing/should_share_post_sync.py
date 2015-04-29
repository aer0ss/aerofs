import os
from lib import ritual
from lib.files import instance_unique_path, wait_file, wait_file_with_content, wait_dir, instance_path
from syncdet.case.sync import sync

def sharer():
    os.makedirs(instance_path('foo', 'bar'))
    wait_file_with_content(instance_path('foo', 'bar', 'baz'), 'qux')
    sync('created')

    r = ritual.connect()
    r.share_folder(instance_unique_path())

    # wait for tag file
    wait_file(instance_path('.aerofs'))
    sync('shared')


def sharee():
    wait_dir(instance_path('foo', 'bar'))
    with open(instance_path('foo', 'bar', 'baz'), 'wb') as f:
        f.write('qux')
    sync('created')

    # wait for tag file
    wait_file(instance_path('.aerofs'))
    sync('shared')


spec = {'entries': [sharer], 'default': sharee}

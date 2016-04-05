import os

from core.multiroot.teamserver import ts_shared_instance_unique_path
from lib import ritual
from lib.files import instance_path, wait_dir, wait_file_with_content
from syncdet.case.sync import sync
from . import assert_synced, assert_not_synced, wait_synced, wait_not_synced

FILES = 25


def client():
    p = os.path.basename(instance_path())
    os.makedirs(instance_path('foo', p))

    r = ritual.connect()

    wait_synced(r, instance_path(), 150)
    wait_synced(r, instance_path('foo', p))
    wait_synced(r, instance_path('foo'))

    r.share_folder(instance_path('foo', p))

    sync('shared')

    for i in range(FILES):
        with open(instance_path('foo', p, 'baz' + str(i)), 'wb') as f:
            f.write('qux')

    # FIXME: this is probably racy
    wait_not_synced(r, instance_path())

    sync('created')

    wait_synced(r, instance_path())
    assert_synced(r, instance_path('foo'))
    assert_synced(r, instance_path('foo', p))
    for i in range(FILES):
        assert_synced(r, instance_path('foo', p, 'baz' + str(i)))

    r.exclude_folder(instance_path('foo', p))

    wait_synced(r, instance_path())
    assert_synced(r, instance_path('foo'))
    assert_not_synced(r, instance_path('foo', p))

    sync('expelled')

    r.include_folder(instance_path('foo', p))

    sync('admitted')

    for i in range(FILES):
        wait_file_with_content(instance_path('foo', p, 'baz' + str(i)), 'qux')

    sync('downloaded')

    for i in range(FILES):
        wait_synced(r, instance_path('foo', p, 'baz' + str(i)))
    wait_synced(r, instance_path())
    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path('foo', p))


def team_server():
    wait_dir(ts_shared_instance_unique_path())
    sync('shared')
    # wait for the files to appear in the shared folder
    # this is VERY important as TS does NOT independently migrate files from a root store to a shared folder
    for i in range(FILES):
        wait_file_with_content(os.path.join(ts_shared_instance_unique_path(), 'baz' + str(i)), 'qux')
    sync('created')
    sync('expelled')
    sync('admitted')
    sync('downloaded')


spec = {'entries': [team_server], 'default': client}

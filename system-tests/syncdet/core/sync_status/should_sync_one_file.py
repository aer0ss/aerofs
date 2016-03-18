import os

from core.multiroot.teamserver import ts_user_instance_unique_path
from lib import ritual
from lib.files import instance_unique_path, wait_file_with_content
from lib.files import wait_dir, instance_path
from syncdet.case.sync import sync
from . import assert_synced, wait_synced, wait_not_synced


def client():
    os.makedirs(instance_unique_path())
    os.makedirs(instance_path('foo', 'bar'))

    r = ritual.connect()

    wait_synced(r, instance_unique_path(), 150)
    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path('foo', 'bar'))

    with open(instance_path('foo', 'bar', 'baz'), 'wb') as f:
        f.write('qux')

    wait_not_synced(r, instance_path('foo', 'bar', 'baz'))

    sync('created')

    wait_synced(r, instance_path('foo', 'bar', 'baz'))
    assert_synced(r, instance_path('foo', 'bar'))
    assert_synced(r, instance_path('foo'))
    assert_synced(r, instance_unique_path())


def team_server():
    wait_dir(os.path.join(ts_user_instance_unique_path(), 'foo', 'bar'))
    wait_file_with_content(os.path.join(ts_user_instance_unique_path(), 'foo', 'bar', 'baz'), 'qux')
    sync('created')


spec = {'entries': [team_server], 'default': client}

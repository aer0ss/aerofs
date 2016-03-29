import os

from core.multiroot.teamserver import ts_user_instance_unique_path
from lib import ritual
from lib.files import instance_path
from lib.files import instance_unique_path, wait_file_with_content
from syncdet.case.sync import sync
from . import assert_synced, wait_synced, wait_not_synced

FILES = 10


def client():
    os.makedirs(instance_unique_path())
    os.makedirs(instance_path('foo'))
    os.makedirs(instance_path('foo', 'bar'))

    r = ritual.connect()

    wait_synced(r, instance_unique_path(), 150)
    wait_synced(r, instance_path('foo', 'bar'))
    wait_synced(r, instance_path('foo'))

    for i in range(FILES):
        with open(instance_path('foo', 'bar', 'baz' + str(i)), 'wb') as f:
            f.write('qux')

    wait_not_synced(r, instance_path('foo', 'bar'))

    sync('created')

    for i in range(FILES):
        os.remove(instance_path('foo', 'bar', 'baz' + str(i)))

    sync('deleted')

    wait_synced(r, instance_unique_path())
    assert_synced(r, instance_path('foo', 'bar'))
    assert_synced(r, instance_path('foo'))


def team_server():
    wait_file_with_content(os.path.join(ts_user_instance_unique_path(), 'foo', 'bar', 'baz' + str(FILES - 1)), 'qux')
    sync('created')
    sync('deleted')


spec = {'entries': [team_server], 'default': client}
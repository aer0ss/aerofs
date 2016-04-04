import os
import shutil

from core.multiroot.teamserver import ts_user_instance_unique_path
from lib import ritual
from lib.files import instance_unique_path
from lib.files import wait_dir, instance_path
from syncdet.case.sync import sync
from . import assert_synced, wait_synced, wait_not_synced

FILES = 10


def client():
    os.makedirs(instance_unique_path())
    os.makedirs(instance_path('foo'))
    os.makedirs(instance_path('foo', 'bar'))
    os.makedirs(instance_path('baz'))

    r = ritual.connect()

    wait_synced(r, instance_unique_path(), 150)
    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path('foo', 'bar'))
    wait_synced(r, instance_path('baz'))

    for i in range(FILES):
        with open(instance_path('foo', 'bar', 'baz' + str(i)), 'wb') as f:
            f.write('qux')

    shutil.move(instance_path('foo', 'bar'), instance_path('baz'))

    wait_not_synced(r, instance_path('baz'))

    sync('moved')

    wait_synced(r, instance_unique_path())
    assert_synced(r, instance_path('foo'))
    assert_synced(r, instance_path('baz'))
    assert_synced(r, instance_path('baz', 'bar'))


def team_server():
    wait_dir(os.path.join(ts_user_instance_unique_path(), 'baz', 'bar'))
    sync('moved')


spec = {'entries': [team_server], 'default': client}

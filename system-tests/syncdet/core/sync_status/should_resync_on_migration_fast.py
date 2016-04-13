import os
import shutil

from core.multiroot.teamserver import ts_user_instance_unique_path
from lib import ritual
from lib.files import instance_path, wait_file_with_content
from syncdet.case.sync import sync
from . import wait_synced

FILES = 25


def client():
    os.makedirs(instance_path('foo', 'bar'))
    os.makedirs(instance_path('bar'))

    r = ritual.connect()

    wait_synced(r, instance_path(), 150)
    wait_synced(r, instance_path('bar'))
    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path('foo', 'bar'))

    for i in range(FILES):
        with open(instance_path('foo', 'bar', 'baz' + str(i)), 'wb') as f:
            f.write('qux')

    r.share_folder(instance_path('foo'))

    shutil.move(instance_path('foo', 'bar'), instance_path('bar'))

    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path())
    wait_synced(r, instance_path('foo'))
    wait_synced(r, instance_path('bar'))
    wait_synced(r, instance_path('bar', 'bar'))
    sync('synced')

    wait_file_with_content(instance_path('bar', 'bar', 'baz' + str(FILES - 1)), 'qux')
    wait_synced(r, instance_path('bar', 'bar', 'baz' + str(FILES - 1)))

    sync('migrated')


def team_server():
    sync('synced')
    wait_file_with_content(os.path.join(ts_user_instance_unique_path(), 'bar', 'bar', 'baz' + str(FILES - 1)), 'qux')
    sync('migrated')


spec = {'entries': [team_server], 'default': client}

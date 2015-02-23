
import os
from lib.app.cfg import get_cfg
from lib.files import instance_unique_path, wait_dir, wait_path_to_disappear
from lib.network_partition import NetworkPartition
from syncdet.case import instance_unique_string
from syncdet.case.sync import sync


def path():
    return os.path.join(instance_unique_path(), "foo")


def subpath():
    return os.path.join(path(), "bar")


def moved_path():
    return os.path.join(instance_unique_path(), "bar")


def create():
    sync(0)
    os.makedirs(subpath())

    sync(1)

    with NetworkPartition():
        sync(2)
        os.rename(path(), os.path.join(get_cfg().get_rtroot(), instance_unique_string()))
        sync(3)

    wait_dir(moved_path())


def delete():
    sync(0)
    wait_dir(subpath())

    sync(1)

    sync(2)

    os.rename(subpath(), moved_path())

    sync(3)

    wait_path_to_disappear(path())


def view():
    with NetworkPartition():
        sync(0)
        sync(1)

    sync(2)
    wait_dir(moved_path())

    sync(3)

    wait_path_to_disappear(path())


spec = {'entries': [create, delete], "default": view}
"""
Reproducing AE encountered by jospain@spainwilliams.com

Yet another argument for not being case-insensitive internally...

"""
import os

from lib import ritual
from lib.cases.start_daemon import start
from lib.cases.stop_daemon import stop
from lib.files import instance_unique_path


def foo_path():
    return os.path.join(instance_unique_path(), "foo")

def Foo_path():
    return os.path.join(instance_unique_path(), "Foo")

def bar_path():
    return os.path.join(instance_unique_path(), "bar")

def Bar_path():
    return os.path.join(instance_unique_path(), "Bar")

def sub_path(path):
    return os.path.join(path, "sub")


def main():
    os.makedirs(sub_path(foo_path()))
    os.makedirs(sub_path(bar_path()))

    # wait for scanner to pick up changes
    r = ritual.connect()
    r.wait_path(sub_path(foo_path()))
    r.wait_path(sub_path(bar_path()))

    stop()

    # problematic rename
    os.rename(bar_path(), Bar_path())
    os.rename(foo_path(), bar_path())

    # the rename is inherently problematic but it only triggers
    # an assertion in TDB if at least one children is missing
    os.rmdir(sub_path(Bar_path()))
    os.rmdir(sub_path(bar_path()))

    start()

    # wait for scanner to pick up change
    # NB: the daemon will DIE
    r = ritual.connect()
    r.wait_path_to_disappear(sub_path(bar_path()))


spec = {'entries': [main]}

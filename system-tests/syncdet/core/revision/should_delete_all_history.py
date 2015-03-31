import os
import shutil
from lib import ritual
from syncdet.case.sync import sync
from syncdet.case.assertion import assertEqual
from lib.files import instance_unique_path, wait_file_with_content, wait_path_to_disappear

CONTENT = "foo bar baz\n"

def test_dir1():
    return os.path.join(instance_unique_path(), "d1")

def test_dir2():
    return os.path.join(instance_unique_path(), "d2")

def test_file1():
    return os.path.join(test_dir1(), "test")

def test_file2():
    return os.path.join(test_dir2(), "test")


def creator():
    os.makedirs(test_dir1())
    os.makedirs(test_dir2())

    with open(test_file1(), "w") as f: f.write(CONTENT)
    with open(test_file2(), "w") as f: f.write(CONTENT)

    sync(0)

    shutil.rmtree(instance_unique_path())

    sync(1)


def observer():
    wait_file_with_content(test_file1(), CONTENT)
    wait_file_with_content(test_file2(), CONTENT)

    sync(0)

    wait_path_to_disappear(instance_unique_path())

    sync(1)

    r = ritual.connect()

    assertEqual(2, len(r.list_rev_children(instance_unique_path())))

    r.delete_revision(instance_unique_path(), None)

    assertEqual(0, len(r.list_rev_children(instance_unique_path())))


spec = { 'entries': [creator], 'default': observer }

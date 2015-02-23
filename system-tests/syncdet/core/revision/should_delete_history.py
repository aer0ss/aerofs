
import os
import time
from lib import ritual
from syncdet.case.sync import sync
from syncdet.case.assertion import assertEqual
from lib.files import instance_unique_path, wait_file_new_content

NUM_VERSIONS = 8

def test_file():
    return os.path.join(instance_unique_path(), "test")

# We have to make sure that either file size or update time change
# with each update. (Two updates in the same interval that don't
# change file size are ignored)
# AER-2077

def gen_contents(key):
    retval = ": "
    for i in xrange(key):
        retval += str(i) + " "
    return retval

# since we change the file size each time, we don't need the sleep;
# however this will make it a little easier to analyze from logs.
# Let's try a variety of sleep amounts less than and greater than
# a second.
#
def creator():
    os.mkdir(instance_unique_path())

    for i in range(NUM_VERSIONS + 1):
        with open(test_file(), "w") as f: f.write(gen_contents(i))
        sync(i)
        time.sleep(0.2 * i)


def observer():
    for i in range(NUM_VERSIONS + 1):
        wait_file_new_content(test_file(), gen_contents(i))
        sync(i)

    r = ritual.connect()

    h = r.list_rev_history(test_file())
    assertEqual(NUM_VERSIONS, len(h))

    for rev in h:
        r.delete_revision(test_file(), rev.index)

    assertEqual(0, len(r.list_rev_history(test_file())))


spec = { 'entries': [creator], 'default': observer }

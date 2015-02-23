"""
When a shared folder is copied by the user the tag file may remain
and needs to be deleted by the daemon when the copied folder is
first scanned.
"""

import os
import shutil
from lib import ritual
from lib.app.cfg import get_cfg
from syncdet.case import instance_unique_string
from lib.files import instance_path
from syncdet.case.assertion import assertFalse
from common import TAG_FILE_NAME
import time

def main():
    folder = instance_path("shared")
    os.makedirs(folder)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)
    assert os.path.exists(os.path.join(folder, TAG_FILE_NAME))

    # copying the file directly into the root anchor opens all sort of race
    # conditions: if the copy is too slow the daemon might delete the file
    # from under our feet or alternatively the scan of the parent folder
    # may finish before the tag file is created and since the notifier will
    # filter out events about the tag file it won't be cleaned up until the
    # next scan, which will cause the test to fail
    # Hence the use of the rtroot as a staging area of sorts. NB: we should
    # use the auxroot instead to ensure atomic rename but this will have to
    # do for now as the tests don't have an easy way of deriving the auxroot
    # and none of the actors place rtroot and root anchor on different
    # partitions
    tmp = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    shutil.copytree(folder, tmp)
    copy = instance_path("copy")
    os.rename(tmp, copy)

    # wait for the daemon to detect it
    r.wait_path(copy)

    # verify the tag folder has been replaced with the tag file
    # Sometimes ritual will find out about the folder while the tag file is in the process of being
    # deleted. Check a few times before concluding that the test has failed
    tries = 10
    tag = os.path.join(copy, TAG_FILE_NAME)
    while tries > 0:
        if os.path.exists(tag):
            tries -= 1
            time.sleep(0.5)
        else:
            break

    assertFalse(os.path.exists(tag))

spec = {'entries': [main]}

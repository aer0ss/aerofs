import functools
import os
import sqlite3
import time
import traceback

from syncdet.case.sync import sync
from syncdet.actors import actor_list
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertTrue

from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from lib.app.cfg import get_cfg
from lib.cases import reinstall
from lib.files import instance_path, wait_file, wait_file_with_content


def user_id():
    return actor_list()[1].aero_userid


def ts_user_instance_unique_path(*p):
    return os.path.join(get_cfg().get_root_anchor(), 'users', user_id(), instance_unique_string(), *p)


def ts_shared_instance_unique_path(*p):
    return os.path.join(get_cfg().get_root_anchor(), 'shared', instance_unique_string(), *p)


def roots():
    r = {}
    n = 0
    con = None
    while ++n < 10:
        try:
            con = sqlite3.connect(os.path.join(get_cfg().get_rtroot(), "conf"))

            for (sid, p) in con.execute('select hex(s),p from r'):
                print('root {}: {}'.format(sid, p))
                r[sid] = p
            return r
        except:
            # (JG) this was seen to happen just before the rtroot is deleted and recreated as
            # part of syncdet setup. The conf db was empty, which caused the query to fail. Since
            # the db is about to get nuked and recreated, it is OK to just close the connection and
            # continue.
            print 'Warning: error in reading from the conf db.'
            traceback.print_exc()
        finally:
            if con is not None:
                con.close()
        time.sleep(POLLING_INTERVAL)

    assertTrue(False)


def ts(ts_uninstall, ts_validate):
    sync("shared")

    wait_file_with_content(ts_shared_instance_unique_path("foo"), "bar")
    # wait for tag file to appear, otherwise duplicate folder will be created
    # on reinstall, which would break the expectations of other actors
    wait_file(ts_user_instance_unique_path(".aerofs"))

    sync("synced")

    r0 = roots()

    ts_uninstall()

    sync("unlinked")

    reinstall.reinstall()

    # wait for all previous roots to be rejoined at the same place
    # NB: the loop is required to avoid race conditions between the
    # daemon writing to the conf db and the test reading from it
    while True:
        if ts_validate(r0, roots()):
            break
        time.sleep(POLLING_INTERVAL)

    sync("reinstalled")


def creator():
    r = ritual.connect()
    os.makedirs(instance_path())
    r.share_folder(instance_path())
    with open(instance_path("foo"), "w") as f:
        f.write("bar")

    sync("shared")
    sync("synced")
    r.exclude_folder(instance_path())

    sync("unlinked")
    sync("reinstalled")
    r.include_folder(instance_path())
    wait_file_with_content(instance_path("foo"), "bar")


def bystander():
    sync("shared")
    wait_file_with_content(instance_path("foo"), "bar")
    sync("synced")
    r = ritual.connect()
    r.exclude_folder(instance_path())

    sync("unlinked")
    sync("reinstalled")
    r.include_folder(instance_path())
    wait_file_with_content(instance_path("foo"), "bar")


def spec(ts_uninstall, ts_validate):
    return {'entries': [functools.partial(ts, ts_uninstall, ts_validate), creator], 'default': bystander}

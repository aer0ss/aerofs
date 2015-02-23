"""
IMPORTANT - Conditions for running tests using these utilities:
  -   The first actor in the config file needs to be a team server configured to use linked storage
  -   The second actor does not have to be the same user as the first, but they must be in the same
      organization

"""
import os
import time

from syncdet.actors import actor_list
from syncdet.case import instance_unique_string
from syncdet.case.assertion import fail

from aerofs_common import exception
from aerofs_common.param import POLLING_INTERVAL
from aerofs_sp import sp as sp_service
from lib import ritual
from aerofs_ritual.id import get_root_sid_bytes
from lib.app.cfg import get_cfg


def user_id():
    return actor_list()[1].aero_userid

def get_instance_unique_sid():
    sp = sp_service.connect()
    # sign in as actor 1 in as SP does not accept password auth for TS user
    sp.sign_in(actor_list()[1])
    while True:
        for folder in sp.list_shared_folders_with_names():
            # print folder["name"], folder["sid"]
            if folder["name"] == instance_unique_string():
                return folder["sid"]
        time.sleep(POLLING_INTERVAL)

def _ts_root_instance_unique_path(root):
    return os.path.join(get_cfg().get_root_anchor(), root)

def ts_user_sid_bytes():
    return get_root_sid_bytes(user_id())

def ts_user_root():
    return os.path.join(get_cfg().get_root_anchor(), 'users', user_id())

def ts_user_instance_unique_path():
    return os.path.join(ts_user_root(), instance_unique_string())

def ts_shared_instance_unique_path():
    return _ts_root_instance_unique_path(os.path.join('shared', instance_unique_string()))


def expect_ritual_exception(func, extype):
    """
    A function wrapper that expects a particular exception type raised from the
    decorated function, otherwise fails the test. Usage:

        def foo(arg1, arg2):
            ...

        expect_ritual_exception(foo, PBException.NOT_FOUND)(arg1, arg2)

    @param ex the exception type the caller expect
    """
    def wrapper(*args, **kwargs):
        try:
            func(*args, **kwargs)
        except exception.ExceptionReply as e:
            if e.get_type() == extype:
                pass
            else:
                fail("got ritual exception " + e + " but expected " + extype.__name__)
        else:
            fail("ritual exception " + extype.__name__ + " is expected")
    return wrapper




def _ts_test(fn, arg):
    return lambda: fn(arg())

def ts_spec(**spec):
    spec["entries"] = [_ts_test(fn, arg) for (fn, arg) in ([spec["teamserver"]] + spec["clients"])]
    return spec

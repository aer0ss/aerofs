import os
from aerofs_common.exception import ExceptionReply
from syncdet.case.assertion import expect_exception
from lib.files import instance_unique_path
from lib import ritual

def main():
    parent = instance_unique_path()
    unshared = os.path.join(parent, "unshared")
    shared = os.path.join(parent, "shared")
    unshared_under_shared = os.path.join(shared, "unshared")

    os.makedirs(unshared)
    os.makedirs(unshared_under_shared)

    r = ritual.connect()
    r.share_folder(shared)

    expect_exception(r.update_acl, ExceptionReply)(unshared, 'foo', ritual.EDITOR)
    expect_exception(r.update_acl, ExceptionReply)(unshared_under_shared, 'foo', ritual.EDITOR)

spec = { 'entries': [main] }

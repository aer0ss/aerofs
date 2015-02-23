from lib.app.cfg import get_cfg
from lib.app.aerofs_proc import run_ui
from lib import ritual
from syncdet.case.assertion import expect_exception
import os
import sys
import ntpath
import socket


# State needs to be reset for further SyncDet tests
def main():
    new_root = os.path.join(get_cfg().get_rtroot(), "AeroFS")

    print "resetting root anchor: {0}".format(new_root)
    r = ritual.connect()

    expect_exception(r.relocate, socket.error)(new_root)
    run_ui()

spec = { 'default': main }

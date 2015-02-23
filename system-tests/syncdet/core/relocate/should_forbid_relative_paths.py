from lib import ritual
from aerofs_common.exception import ExceptionReply
from syncdet.case.assertion import expect_exception

def main():
    r = ritual.connect()

    # relocating of relative folder should be forbidden
    expect_exception(r.relocate, ExceptionReply)("..")

spec = { 'default': main }
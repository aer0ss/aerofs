import os
from lib import ritual
from aerofs_common.exception import ExceptionReply
import lib.files
from syncdet.case.assertion import expect_exception

def main():
    root = lib.files.instance_unique_path()
    folder = os.path.join(root, "folder")
    subfolder = os.path.join(folder, "subfolder")
    subsubfolder = os.path.join(subfolder, "subsubfolder")

    # create objects
    os.makedirs(subsubfolder)

    # share subfolder
    r = ritual.connect()
    r.share_folder(subfolder)

    # sharing of the parent folder should be forbidden
    expect_exception(r.share_folder, ExceptionReply)(folder)

    # sharing of the child bfolder should be forbidden
    expect_exception(r.share_folder, ExceptionReply)(subsubfolder)

spec = { 'entries': [main] }
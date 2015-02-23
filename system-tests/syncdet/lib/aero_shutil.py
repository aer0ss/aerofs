"""
This module is wrapper for shutil.rmtree and is necessary because:
1. Adding a custom icon for shared folders involves creating a special Desktop.ini
file for that folder and making the folder a system folder.
2. The above step will set a read-only bit on the shared folder to indicate
that the special behavior reserved for Desktop.ini should be enabled
3. At AeroFS, we use windows named pipes to communicate between the GUI-Daemon.
4. Cygwin python does not support windows named pipes well so we invoke
win python from cyg python.
5. Win python refuses to delete(using a simple shutil.rmtree) read only folders
6. Hence, we need to invoke the rmdir command to remove the folder.
"""
import sys
import os
from shutil import *

shutil_rmtree = rmtree

def rmtree(path, **kwargs):
    if 'win32' in sys.platform.lower():
        os.system('rmdir /S /Q \"{}\"'.format(path))
    else:
        shutil_rmtree(path, **kwargs)

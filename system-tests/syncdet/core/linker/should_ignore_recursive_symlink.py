"""
On Linux, we have to manually recurse into subfolders and establish watches on
all of them.  This means we have to be careful about recursive symlinks.

This test creates a symlink that points to its containing folder, then checks
that future changes to the filesystem continue to be noticed.

"""
import os

from lib import files, ritual


def create_symlink(name="link"):
    pwd = os.getcwd()
    os.chdir(files.instance_unique_path())
    os.symlink(".", name)
    os.chdir(pwd)

def tester():
    if not hasattr(os, "symlink"):
        print "Can't test symlink behavior on non-Unix platforms"
        return

    first_folder_path = os.path.join(files.instance_unique_path(), "1")
    os.makedirs(first_folder_path)
    r = ritual.connect()
    r.wait_path(first_folder_path)

    # Create symlink if the platform supports it
    # (Cygwin's Python supports it, but Win32's doesn't)
    create_symlink("link")

    second_folder_path = os.path.join(first_folder_path, "2")
    os.mkdir(second_folder_path)
    r.wait_path(second_folder_path)


def ignorer():
    return

spec = {'entries': [tester], 'default': ignorer, 'timeout':7 }

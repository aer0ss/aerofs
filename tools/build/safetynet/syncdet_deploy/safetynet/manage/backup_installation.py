import os
import sys

from syncdet import case
from lib.app.cfg import get_cfg
import shutil
from safetynet import param

def ignore_special(folder, children):
    """
    An exclusion filter for shutil.copytree that ignores anything that isn't a
    standard file, folder, or symlink.  This is needed to avoid trying to copy
    socket files in the rtroot on Linux and OSX.
    """
    to_ignore = []
    for child in children:
        path = os.path.join(folder, child)
        if not os.path.isfile(path) and not os.path.isdir(path) and not os.path.islink(path):
            to_ignore.append(child)
    return to_ignore

def backup_installation():
    user_data_path = case.user_data_folder_path()
    cfg = get_cfg()
    app_root = cfg.get_approot()
    rt_root = cfg.get_rtroot()

    # Build the destination paths
    dst_app_root = os.path.join(user_data_path, param.APP_ROOT_BACKUP_DIR)
    dst_rt_root = os.path.join(user_data_path, param.RT_ROOT_BACKUP_DIR)

    # Remove the destination paths if they exist
    shutil.rmtree(dst_app_root, ignore_errors=True)
    shutil.rmtree(dst_rt_root, ignore_errors=True)

    # Copy the contents of app_root and rt_root to the backup
    # destination folders
    shutil.copytree(app_root, dst_app_root, ignore=ignore_special)
    shutil.copytree(rt_root, dst_rt_root, ignore=ignore_special)

# The timeout is large (5 minutes) because the SafetyNet clients are running in VMs
# hosted on the same VM host as the CI VMs. Disk I/O becomes unbearably slow
# at times and this helps avoid transient failures due to this slow down.
spec = { 'default': backup_installation , 'timeout': 5*60 }

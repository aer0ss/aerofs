import os
import sys

from syncdet import case
from safetynet import param
from lib.app.cfg import get_cfg
from lib import aero_shutil as shutil

def rollback_installation():
    user_data_path = case.user_data_folder_path()
    cfg = get_cfg()
    app_root = cfg.get_approot()
    rt_root = cfg.get_rtroot()

    # Build the source paths
    src_app_root = os.path.join(user_data_path, param.APP_ROOT_BACKUP_DIR)
    src_rt_root = os.path.join(user_data_path, param.RT_ROOT_BACKUP_DIR)

    if not os.path.exists(src_app_root) or not os.path.exists(src_rt_root):
        raise Exception("no backups available")

    # Remove the original installation paths
    shutil.rmtree(app_root, ignore_errors=True)
    shutil.rmtree(rt_root, ignore_errors=True)

    # Copy the contents of the app_root and rt_root backup directories to
    # the original install directories
    shutil.copytree(src_app_root, app_root)
    shutil.copytree(src_rt_root, rt_root)

    # Touch the "ignoredbtampering" file in the rtroot.
    # The daemon detects when the inode number of the database file changes,
    # because in most cases this indicates that one node in the system was
    # rolled back in time, which can break the distributed algorithm.
    # In the unique case of safetynet, however, we roll ALL clients back to a
    # snapshot at the same time.  This is safe, and as a result, we can safely
    # disable the DB tampering detection.
    with open(os.path.join(rt_root, "ignoredbtampering"), "wb") as f:
        pass

# The timeout is large (5 minutes) because the SafetyNet clients are running in VMs
# hosted on the same VM host as the CI VMs. Disk I/O becomes unbearably slow
# at times and this helps avoid transient failures due to this slow down.
spec = { 'default': rollback_installation, 'timeout': 5*60 }

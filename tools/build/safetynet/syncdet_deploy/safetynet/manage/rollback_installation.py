import os
import sys
import shutil

from syncdet import case
from safetynet import param
from lib.app.cfg import get_cfg

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

# The timeout is large (5 minutes) because the SafetyNet clients are running in VMs
# hosted on the same VM host as the CI VMs. Disk I/O becomes unbearably slow
# at times and this helps avoid transient failures due to this slow down.
spec = { 'default': rollback_installation, 'timeout': 5*60 }

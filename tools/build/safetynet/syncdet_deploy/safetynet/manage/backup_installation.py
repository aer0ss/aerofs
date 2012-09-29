import os
import sys
import shutil

from syncdet import case
from lib import app
from safetynet import param

def backup_installation():
    user_data_path = case.user_data_folder_path()
    app_root = app.app_root_path()
    rt_root = app.rt_root_path()

    # Build the destination paths
    dst_app_root = os.path.join(user_data_path, param.APP_ROOT_BACKUP_DIR)
    dst_rt_root = os.path.join(user_data_path, param.RT_ROOT_BACKUP_DIR)

    # Remove the destination paths if they exist
    shutil.rmtree(dst_app_root, ignore_errors=True)
    shutil.rmtree(dst_rt_root, ignore_errors=True)

    # Copy the contents of app_root and rt_root to the backup
    # destination folders
    shutil.copytree(app_root, dst_app_root)
    shutil.copytree(rt_root, dst_rt_root)

# The timeout is large (5 minutes) because the SafetyNet clients are running in VMs
# hosted on the same VM host as the CI VMs. Disk I/O becomes unbearably slow
# at times and this helps avoid transient failures due to this slow down.
spec = { 'default': backup_installation , 'timeout': 5*60 }

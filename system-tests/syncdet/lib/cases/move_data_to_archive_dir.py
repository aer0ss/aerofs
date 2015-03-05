"""
This case moves data from the rtroot and the root anchor into ~/archive
The idea is that the contents of this folder will be tar'd by syncdet and
archived by teamcity. This is helpful because the directory locations change
between operating systems, and are different for client/teamserver. This case
abstracts that away and tells syncdet it can find the data at one easy-to-find
location.

It also de-obfuscates ~/archive/rtroot/daemon.log and ~/archive/rtroot/cli.log
based on a map file that must be deployed to ~/syncdet/deploy/aerofs.map

"""
import os
import sys
import shutil

from syncdet import case

from ..app.cfg import get_cfg
from ..app.install import rm_rf, cleanup_win_root_anchor
from ..app.aerofs_proc import stop_all
from lib import jretrace


def move_data_to_archive_dir():
    archive_dir = os.path.join(os.path.expanduser('~'), 'archive')
    if 'win32' in sys.platform:
        cleanup_win_root_anchor(archive_dir)
        os.rmdir(archive_dir)
    else:
        # make sure we only archive this suite's data
        rm_rf(archive_dir)

    # kill aerofs processes so the rtroot is unchanged during the copy
    stop_all()

    cfg = get_cfg()
    rtroot = cfg.get_rtroot()
    root_anchor = cfg.get_root_anchor()

    os.makedirs(archive_dir)
    os.rename(rtroot, os.path.join(archive_dir, 'rtroot'))
    os.rename(root_anchor, os.path.join(archive_dir, 'root_anchor'))

    p = os.path.dirname(root_anchor)
    # copy all auxroots-looking folders for simplicity (install should clean old ones)
    for d in os.listdir(p):
        if d.startswith('.aerofs.aux.'):
            os.rename(os.path.join(p, d), os.path.join(archive_dir, d))


def unobfuscate_logs():
    log_dir = os.path.join(os.path.expanduser('~'), 'archive', 'rtroot')
    map_file = os.path.join(case.deployment_folder_path(), 'aerofs.map')
    for log_name in ['daemon', 'cli']:
        log_file = os.path.join(log_dir, log_name + '.log')
        if not os.path.isfile(log_file):
            continue
        output_file = os.path.join(log_dir, log_name + '.unobf.log')
        print 'de-obfuscating', log_file
        with open(output_file, 'w') as wf:
            for line in jretrace.main(map_file, log_file):
                wf.write(line + '\n')


def main():
    move_data_to_archive_dir()
    unobfuscate_logs()


spec = {
    'default': main,
    'timeout': 120
}

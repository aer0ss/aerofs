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


def _is_file_or_dir(p):
    return os.path.isfile(p) or os.path.isdir(p)


def _ignore(path, files):
    return [e for e in files if not _is_file_or_dir(os.path.join(path, e))]


def move_data_to_archive_dir():
    archive_dir = os.path.join(os.path.expanduser('~'), 'archive')
    if 'win32' in sys.platform:
        cleanup_win_root_anchor(archive_dir)
        magic_prefix = u"\\\\?\\"
    else:
        # make sure we only archive this suite's data
        rm_rf(archive_dir)
        magic_prefix = ""

    archive_dir_prefixed = magic_prefix + os.path.join(os.path.expanduser('~'), 'archive')
    cfg = get_cfg()
    # kill aerofs processes so the rtroot is unchanged during the copy
    stop_all()

    # N.B. shutil.copytree() creates the destination dir and all parents.Copy
    # everything in the rtroot except the unix domain socket files.
    shutil.copytree(cfg.get_rtroot(), os.path.join(archive_dir_prefixed, 'rtroot'), symlinks=True, ignore=_ignore)

    shutil.copytree(magic_prefix + cfg.get_root_anchor(), os.path.join(archive_dir_prefixed, 'root_anchor'), symlinks=True)


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

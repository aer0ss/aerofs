"""
This case moves data from the rtroot and the root anchor into ~/archive
The idea is that the contents of this folder will be tar'd by syncdet and
archived by teamcity. This is helpful because the directory locations change
between operating systems, and are different for client/teamserver. This case
abstracts that away and tells syncdet it can find the data at one easy-to-find
location.

"""
import os

from syncdet import case

from ..app.cfg import get_cfg
from ..app.install import rm_rf
from ..app.aerofs_proc import stop_all


def move_data_to_archive_dir():
    archive_dir = os.path.join(os.path.expanduser('~'), 'archive')
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


def main():
    move_data_to_archive_dir()


spec = {
    'default': main,
    'timeout': 120
}

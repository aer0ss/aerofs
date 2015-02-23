"""
Dump the leaf node paths to the file in user_data
"""

from ..app.cfg import get_cfg
from syncdet.case import user_data_folder_path
import os

# Use ".log" suffix since browsers seem to open that type in-place
_DUMP_FILE = 'root_anchor.log'


def main():

    with open(os.path.join(user_data_folder_path(), _DUMP_FILE), 'w') as f:
        f.write('------------------------------------------------------\n')

        for dirpath, dirnames, filenames in os.walk(get_cfg().get_root_anchor()):
            if not filenames and not dirnames:
                f.write('d {0}\n'.format(dirpath.encode('utf8')))

            f.writelines(['f {0}\n'.format(
                os.path.join(dirpath, name).encode('utf8'))
                for name in filenames])
            f.flush()


spec = { 'default':main }
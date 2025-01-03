"""
Check that the daemon can create a seed file and that the seed file is used on reinstall

NB: ideally we'd test unlink instead of manually creating the seed file, unfortunately the
unlink logic is fully contained in the UI so it cannot be tested in a regular syncdet env
where we only work with a "bare" daemon
"""

import os
import binascii
from lib.files import instance_unique_path
from lib import ritual
from aerofs_ritual.id import get_root_sid_bytes
from syncdet.case import local_actor
from syncdet.case.assertion import assertEqual
from lib.app import aerofs_proc
from lib.app.install import get_installer


def main():
    os.makedirs(instance_unique_path())
    sid = get_root_sid_bytes(local_actor().aero_userid)

    r = ritual.connect()
    oid_old = r.test_get_object_identifier(instance_unique_path()).oid
    print binascii.hexlify(oid_old)

    r.create_seed_file(sid)

    aerofs_proc.stop_all()

    installer = get_installer()
    # remove conf db to force rtroot cleanup/reinstall
    os.remove(os.path.join(installer.get_rtroot(), "conf"))
    installer.configure_unattended_setup()
    installer.install(clean_install=False)

    # check that the SOID is unchanged
    r = ritual.connect()
    oid_new = r.test_get_object_identifier(instance_unique_path()).oid
    print binascii.hexlify(oid_new)
    assertEqual(oid_old, oid_new)

spec = {'entries': [main]}

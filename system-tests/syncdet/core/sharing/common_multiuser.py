"""
This file, its usage and its name(common_multiuser) refer to the presence of 2 or more
actor accounts rather than the TS. Please don't get confused.
"""
import time
from lib import ritual
from lib.app.cfg import get_cfg
from syncdet import case
from syncdet.case.assertion import assertTrue
from lib.files import wait_file_with_content
from aerofs_sp import sp as sp_service
import os
from aerofs_common.convert import store_relative_to_pbpath
from aerofs_common import param

FILE_CONTENTS = "Jean Valjean"
FILENAME = "24601"

# Don't change this note. It lets us easily filter out emails from running this test.
NOTE = "This is a syncdet test. Bananas."

def _create_acl(acl_level):
    acl = {}
    for actor in case.actors.actor_list():
        if actor.aero_userid != case.local_actor().aero_userid:
            acl[actor.aero_userid] = acl_level
    return acl

def share_admitted_internal_dir(path, acl_level):
    """
    This function shares a admitted internal folder. It is
    different from share_external_dir and share_expelled_internal_dir
    as it tries to create a file within the folder and
    check for sharing by making sure that the file also appears
    in the the sharee. This is fine for admitted folders
    as the file will actually propagate to the sharee.

    However, this is a futile exercise with unlinked and expelled
    folders as creating files in unlinked and expelled folders will
    not propagate the file to the sharee.
    Note: The function doesn't handle linked external folders well.
    Look at share_pbpath for it.
    """
    if not os.path.exists(path):
        os.makedirs(path)

    file_path = os.path.join(path, FILENAME)
    with open(file_path, 'w') as f:
        f.write(FILE_CONTENTS)

    acl = _create_acl(acl_level)
    ritual.connect().share_folder(path, acl=acl, note=NOTE)

    print("share folder %s" % path)

def share_expelled_internal_dir(path, acl_level):
    acl = _create_acl(acl_level)
    ritual.connect().share_folder(path, acl=acl, note=NOTE)

def share_external_dir(sid, acl_level):
    # Note: This files shares both linked/unlinked external dirs.
    acl = _create_acl(acl_level)
    ritual.connect().share_pbpath(store_relative_to_pbpath(sid, ""), acl)

def kickout_dir(path):
    for actor in case.actors.actor_list():
        if actor.aero_userid != case.local_actor().aero_userid:
            ritual.connect().delete_acl(path, actor.aero_userid)

def kickout_dir_pbpath(pbpath):
    """
    This function deals with kicking out external folders.
    """
    for actor in case.actors.actor_list():
        if actor.aero_userid != case.local_actor().aero_userid:
            ritual.connect().delete_acl_pbpath(pbpath, actor.aero_userid)

def update_dir_acl(path, acl_level):
    for actor in case.actors.actor_list():
        if actor.aero_userid != case.local_actor().aero_userid:
            ritual.connect().update_acl(path, actor.aero_userid, acl_level)

def update_dir_acl_pbpath(pbpath, acl_level):
    """
    This function deals with updating acls for external folders.
    """
    for actor in case.actors.actor_list():
        if actor.aero_userid != case.local_actor().aero_userid:
            ritual.connect().update_acl_pbpath(pbpath, actor.aero_userid, acl_level)

def _assert_acl_changed(folder_name, acl_level):
    sp = sp_service.connect()
    sp.sign_in()
    acl_updated = False
    for shared_folder_names_and_permissions in sp.list_shared_folders_names_and_user_permissions():
        if folder_name == shared_folder_names_and_permissions['name']:
            for up_and_state in shared_folder_names_and_permissions['user_permissions_and_state']:
                if case.local_actor().aero_userid == up_and_state.user.user_email:
                    acl_updated = acl_level == up_and_state.permissions.permission
    assertTrue(acl_updated)

def _wait_to_get_kicked_out(folder_name):
    while folder_name in ritual.connect().list_shared_folders_names():
         time.sleep(param.POLLING_INTERVAL)

def assert_unlinked_folder_acl_changed(path, acl_level):
    folder_name = os.path.relpath(path, get_cfg().get_rtroot())
    _assert_acl_changed(folder_name, acl_level)

def assert_expelled_folder_acl_changed(path, acl_level):
    folder_name = os.path.relpath(path, get_cfg().get_root_anchor())
    _assert_acl_changed(folder_name, acl_level)

def wait_unlinked_folder_kicked_out(path):
    folder_name = os.path.relpath(path, get_cfg().get_rtroot())
    _wait_to_get_kicked_out(folder_name)

def wait_expelled_folder_kicked_out(path):
    folder_name = os.path.relpath(path, get_cfg().get_root_anchor())
    _wait_to_get_kicked_out(folder_name)

def _wait_for_shared_folder(folder_name, path, is_admitted):
    """
    This function waits for the sharee to get an invitation to
    an shared folder to assert that we are able to share folders.
    As mentioned in comments for share_admitted_internal_dir, if we share an admitted folder
    we check if the file within the share folder also propagates to the sharee,
    however in unlinked/expelled shared folder case we don't check for the file.
    """
    r = ritual.connect()
    shared_folder = None
    for invitation in r.list_shared_folder_invitations():
        if invitation.folder_name == folder_name:
            r.join_shared_folder(invitation.share_id)
            shared_folder = invitation
            if is_admitted:
                file_path = os.path.join(path, FILENAME)
                wait_file_with_content(file_path, FILE_CONTENTS)
            break
    assertTrue(shared_folder)

def wait_for_expelled_shared_folder(path):
    folder_name = os.path.relpath(path, get_cfg().get_root_anchor())
    _wait_for_shared_folder(folder_name, path, False)

def wait_for_external_shared_folder(path):
    # External folder, so get_rtroot because that is where external folders are created.
    folder_name = os.path.relpath(path, get_cfg().get_rtroot())
    _wait_for_shared_folder(folder_name, path, False)

def wait_for_admitted_shared_folder(path):
    # Note: This function doesn't deal with linked external roots well.
    folder_name = os.path.relpath(path, get_cfg().get_root_anchor())
    _wait_for_shared_folder(folder_name, path, True)

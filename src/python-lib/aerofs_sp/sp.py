"""
This should only be used by syncdet, as it has dependencies on syncdet modules.
In reality (PH) this script and ritual.py belong in syndet_test/lib. The goal is to
keep PB related stuff in one place but to also keep syncdet stuff out of python-lib.
Both this and ritual.py have syncdet dependencies but are in python-lib. The PB stuff
should probably be extracted out and left here while the rest goes into syndet_test/lib.

"""
from connection import SyncConnectionService
from gen import sp_pb2
from param import SP_PROTO_VERSION
from syncdet.case import local_actor


def connect():
    aero_host = local_actor().aero_host
    sp_url = 'https://{}:4433/sp'.format(aero_host)
    conn = SyncConnectionService(sp_url, SP_PROTO_VERSION)
    sp_service = sp_pb2.SPServiceRpcStub(conn)
    return _SPServiceWrapper(sp_service)

class _SPServiceWrapper(object):
    def __init__(self, rpc_service):
        self._service = rpc_service

    def sign_in(self, actor=None):
        if actor is None:
            actor = local_actor()
        user_id = actor.aero_userid
        password = actor.aero_password.encode("utf-8")
        self._service.credential_sign_in(user_id, password)

    def provide_second_factor(self, second_factor):
        return self._service.provide_second_factor(second_factor)

    def provide_backup_code(self, backup_code):
        return self._service.provide_backup_code(backup_code)

    def list_shared_folders(self):
        reply = self._service.get_acl(0)
        sids = [s.store_id for s in reply.store_acl]
        return sids

    def list_shared_folders_with_names(self):
        sids = self.list_shared_folders()
        pb_folders = self._service.list_shared_folders(sids)
        return [{"name": f.name, "sid": f.store_id.encode("hex")} for f in pb_folders.shared_folder]

    def list_shared_folders_names_and_user_permissions(self):
        sids = self.list_shared_folders()
        pb_folders = self._service.list_shared_folders(sids)
        return [{"name": f.name, "user_permissions_and_state": f.user_permissions_and_state} \
            for f in pb_folders.shared_folder]


    def leave_shared_folder(self, sid):
        self._service.leave_shared_folder(sid)

    def destroy_shared_folder(self, sid):
        self._service.leave_shared_folder(sid)

    def list_pending_folder_invitations(self):
        reply = self._service.list_pending_folder_invitations()
        return reply.invitation

    def ignore_shared_folder_invitation(self, sid):
        self._service.ignore_shared_folder_invitation(sid)

    def get_mobile_access_code(self):
        return self._service.get_mobile_access_code().accessCode

    def add_user_to_whitelist(self, userid):
        return self._service.add_user_to_whitelist()

    def unlink(self, did, erase=False):
        self._service.unlink_device(did, erase)

    def set_quota(self, quota):
        self._service.set_quota(quota)

    def get_quota(self):
        return self._service.get_quota()

    def remove_quota(self):
        return self._service.remove_quota()

    def setup_two_factor(self):
        return self._service.setup_two_factor().secret

    def set_two_factor_enforcement(self, enforce, current_code=None, user_id=None):
        return self._service.set_two_factor_enforcement(enforce, current_code, user_id)

    def get_two_factor_setup_enforcement(self):
        return self._service.get_two_factor_setup_enforcement()

    def set_two_factor_setup_enforcement(self, level):
        return self._service.set_two_factor_setup_enforcement(level)


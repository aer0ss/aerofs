"""
This should only be used by syncdet, as it has dependencies on syncdet modules.
In reality (PH) this script and ritual.py belong in syndet_test/lib. The goal is to
keep PB related stuff in one place but to also keep syncdet stuff out of python-lib.
Both this and ritual.py have syncdet dependencies but are in python-lib. The PB stuff
should probably be extracted out and left here while the rest goes into syndet_test/lib.
"""
from connection import SyncConnectionService
from gen import sp_pb2
from scrypt import scrypt
from lib import param
from lib.app import cfg
from syncdet.case import local_actor

def connect():
    sp_url = local_actor().aero_sp_url
    sp_proto_version = param.SP_PROTO_VERSION
    conn = SyncConnectionService(sp_url, sp_proto_version)
    sp_service = sp_pb2.SPServiceRpcStub(conn)
    return _SPServiceWrapper(sp_service)

class _SPServiceWrapper(object):
    def __init__(self, rpc_service):
        self._service = rpc_service

    def sign_in(self):
        user_id = local_actor().aero_userid
        password = local_actor().aero_password
        scrypted_password = scrypt(password, user_id)
        self._service.sign_in(user_id, scrypted_password)

    def list_shared_folders(self):
        sids = []
        reply = self._service.get_acl(0)
        for s in reply.store_acl:
            sids.append(s.store_id)
        return sids

    def list_shared_folders_with_names(self):
        sids = self.list_shared_folders()
        names = self._service.get_shared_folder_names(sids)
        zipped_array = zip(sids, names.folder_name)
        shared_folders = [
            {"name":name, "sid":sid.encode("hex")} for (sid, name) in zipped_array
        ]
        return shared_folders

    def leave_shared_folder(self, sid):
        self._service.leave_shared_folder(sid)

    def list_pending_folder_invitations(self):
        reply = self._service.list_pending_folder_invitations()
        return reply.invitation

    def ignore_shared_folder_invitation(self, sid):
        self._service.ignore_shared_folder_invitation(sid)

"""
Ritual defines a Protobuf-based RPC interface to communicate with the AeroFS
Daemon process, specifically the core.

This module provides an an object-oriented python wrapper for the Ritual RPC
interface. Call 'connect' for a reference to an wrapper object, on which Ritual
RPC methods can be called.  For example

    from aerofs_ritual import ritual

    r = ritual.connect() # by default connects to localhost
    r.share_folder(...)
"""

import socket
import time
import connection
from lib import convert
from lib import param
from aerofs_common import exception
from lib import app
from gen import common_pb2, ritual_pb2
from gen.common_pb2 import PBException, PBSubjectRolePair

def connect(rpc_host_addr = 'localhost', rpc_host_port = None, user = None):
    if rpc_host_port is None: rpc_host_port = app.cfg.ritual_port()
    if user is None: user = app.user()
    ritual_service = ritual_pb2.RitualServiceRpcStub(
            connection.SyncConnectionService(rpc_host_addr, rpc_host_port))
    return _RitualServiceWrapper(ritual_service, user)

def wait():
    while True:
        try:
            connect()
            break
        except socket.error:
            time.sleep(param.POLLING_INTERVAL)

class _RitualServiceWrapper(object):
    """
    Class that wraps the Ritual Protobuf-generated RPC stubs.
    The Ritual interface is a means to communicate with an AeroFS daemon.

    All the methods that operate on logical objects wait until the object is
    recognized by the Daemon, except for *_no_wait() methods. This is necessary
    because test cases usually create physical objects on the filesystem first
    and then call Ritual immediataely to manipulate them. Without the waiting
    the Daemon may throw object-not-found exceptions.
    """

    def __init__(self, rpc_service, user):
        self._service = rpc_service
        self._user = user

    def heartbeat(self):
        self._service.heartbeat()

    def share_folder(self, path, acl = {}, note = ""):
        """
        @param acl a dict of {subject:role}
        """
        pbpath = self.wait_path(path)
        # folder sharing requires at least one invitee
        if not acl:
            acl = {"foo@bar.baz": common_pb2.EDITOR}
        srps = self._convert_acl(acl)
        return self._service.share_folder(pbpath, srps, note)

    @staticmethod
    def _convert_acl(acl):
        srps = []
        for key in acl:
            srp = PBSubjectRolePair()
            srp.subject = key
            srp.role = acl[key]
            srps.append(srp)
        return srps

    def list_shared_folders(self):
        r = []
        for p in self._service.list_shared_folders().path:
            r.append(convert.pbpath_to_absolute(p))
        return r

    def list_shared_folder_invitations(self):
        return self._service.list_shared_folder_invitations().invitation

    def join_shared_folder(self, shared_folder_code):
        self._service.join_shared_folder(shared_folder_code)

    def leave_shared_folder(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        self._service.leave_shared_folder(pbpath)

    def exclude_folder(self, path):
        pbpath = self.wait_path(path)
        return self._service.exclude_folder(pbpath)

    def include_folder(self, path):
        pbpath = self.wait_path(path)
        return self._service.include_folder(pbpath)

    def list_excluded_folders(self):
        r = []
        for p in self._service.list_excluded_folders().path:
            r.append(convert.pbpath_to_absolute(p))
        return r

    def get_object_attributes_no_wait(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.get_object_attributes(self._user, pbpath)

    def get_object_attributes(self, path):
        pbpath = self.wait_path(path)
        return self._service.get_object_attributes(self._user, pbpath)

    def get_children_attributes(self, path):
        """
        @return a dict {name:attrib} where name is a string and attrib is a
        PBObjectAttributes
        """
        pbpath = self.wait_path(path)
        reply = self._service.get_children_attributes(self._user, pbpath)
        ret = {}
        for i in range(0, len(reply.children_name)):
            ret[reply.children_name[i]] = reply.children_attributes[i]
        return ret

    def import_file(self, target, source):
        pbpath = convert.absolute_to_pbpath(target)
        self.import_pbpath(pbpath, source)

    def import_pbpath(self, pbpath, source):
        self._service.import_file(pbpath, source)

    def export_file(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        self.export_pbpath(pbpath)

    def export_pbpath(self, pbpath):
        try:
            return self._service.export_file(pbpath).dest
        except exception.ExceptionReply as e:
            if e.get_type() == PBException.NOT_FOUND:
                return None
            else:
                raise e

    def create_object(self, path, directory):
        pbpath = convert.absolute_to_pbpath(path)
        self.create_pbpath(pbpath, directory)

    def create_pbpath(self, pbpath, directory):
        self._service.create_object(pbpath, directory)

    def delete_object(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        self.delete_pbpath(pbpath)

    def delete_pbpath(self, pbpath):
        self._service.delete_object(pbpath)

    def move_object(self, pathFrom, pathTo):
        pbfrom = convert.absolute_to_pbpath(pathFrom)
        pbto = convert.absolute_to_pbpath(pathTo)
        self._service.move_object(pbfrom, pbto)

    def test_get_object_identifier(self, path):
        pbpath = self.wait_path(path)
        return self._service.test_get_object_identifier(pbpath)

    def update_acl(self, path, subject, role):
        pbpath = self.wait_path(path)
        self._service.update_acl(self._user, pbpath, subject, role)

    def delete_acl(self, path, subject):
        pbpath = self.wait_path(path)
        self._service.delete_acl(self._user, pbpath, subject)

    def get_acl(self, path):
        """
        Returns a dict {subject:role} for a shared folder path

        Args:
            path: shared folder path

        Returns:
            dict consisting of subject:role pairs. subject (string) is a
            username for which a permission exists. role is an int whose
            value corresponds to the value of the PBRole enum

        Raises:
            PBException with type = NOT_SHARED if the path is not a shared
            folder path
        """
        pbpath = self.wait_path(path)
        reply = self._service.get_acl(self._user, pbpath)

        acl = {}
        for pair in reply.subject_role:
            acl[pair.subject] = pair.role # role is an int

        return acl

    def list_rev_children(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.list_rev_children(pbpath).child

    def list_rev_history(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.list_rev_history(pbpath).revision

    def export_revision(self, path, index):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.export_revision(pbpath, index).dest

    def delete_revision(self, path, index):
        pbpath = convert.absolute_to_pbpath(path)
        self._service.delete_revision(pbpath, index)

    def get_sync_status(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.get_sync_status(pbpath)

    def is_synced(self, path):
        # When the file is new, we might get a not found reply. In this case we consider the file
        # not synced.
        try:
            status = self.get_sync_status(path)
        except exception.ExceptionReply:
            return False

        synced = False
        for dstatus in status.status:
            if dstatus.status == ritual_pb2.PBSyncStatus.IN_SYNC:
                synced = True
                break

        return synced

    def get_path_status(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        return self._service.get_path_status(pbpath)

    def relocate(self, absolute_path, sid=None):
        self._service.relocate(absolute_path, sid)

    def pause_syncing(self):
        self._service.pause_syncing()

    def resume_syncing(self):
        self._service.resume_syncing()

    def list_conflicts(self):
        result = {}
        for conflict in self._service.list_conflicts().conflict:
            result[convert.pbpath_to_absolute(conflict.path)] = conflict.branch_count
        return result

    def delete_conflict(self, path, kidx):
        self._service.delete_conflict(convert.absolute_to_pbpath(path), int(kidx))

    def get_activities(self, max_results, **kwargs):
        reply = self._service.get_activities(kwargs.get("brief", False),
                                             max_results,
                                             kwargs.get("token", None))
        return reply.activity

    def get_last_activity_index(self):
        return self._service.get_activities(True, 1, None).page_token

    def shutdown(self):
        while True:
            try:
                self._service.shutdown()
            except exception.ExceptionReply as e:
                if e.get_type() == PBException.INDEXING:
                    time.sleep(1)
                    continue
                raise
            except socket.error:
                # the daemon is expected to disconnect the socket when shutting down
                pass
            else:
                raise IOError("the daemon didn't shut down properly")
            break

    def test_pause_linker(self):
        self._service.test_pause_linker()

    def test_resume_linker(self):
        self._service.test_resume_linker()

    def test_log_send_defect(self):
        self._service.test_log_send_defect()

    def wait_path(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        self.wait_pbpath(pbpath)
        return pbpath

    def wait_pbpath(self, pbpath):
        """
        Wait until the Daemon has recognized the given path
        """
        while True:
            try:
                self._service.get_object_attributes(self._user, pbpath)
                return
            except exception.ExceptionReply as e:
                if e.get_type() == PBException.NOT_FOUND:
                    time.sleep(param.POLLING_INTERVAL)
                else:
                    raise e

    def wait_path_to_disappear(self, path):
        pbpath = convert.absolute_to_pbpath(path)
        self.wait_pbpath_to_disappear(pbpath)

    def wait_pbpath_to_disappear(self, pbpath):
        try:
            while True:
                self._service.get_object_attributes(self._user, pbpath)
                time.sleep(param.POLLING_INTERVAL)
        except exception.ExceptionReply as e:
            if e.get_type() == PBException.NOT_FOUND: pass
            else: raise e

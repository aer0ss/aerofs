import unittest
from pyramid import testing
from mock import Mock
from aerofs_web import helper_functions
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub, ListSharedFoldersReply
from aerofs_common._gen.common_pb2 import EDITOR, OWNER

class GetSharedFolderTest(unittest.TestCase):
    def setUp(self):
        # TODO (WW) move these stub setup steps to a common super class
        self.config = testing.setUp()
        self.stub = SPServiceRpcStub(None)
        helper_functions.get_rpc_stub = Mock(return_value=self.stub)

        self._mock_list_shared_folders()

    def tearDown(self):
        testing.tearDown()

    def _mock_list_shared_folders(self):
        reply = ListSharedFoldersReply()
        reply.total_count = 2
        self._add_shared_folder(reply)
        self._add_shared_folder(reply)

        self.stub.list_shared_folders = Mock(return_value=reply)

    def _add_shared_folder(self, reply):
        folder = reply.shared_folder.add()

        folder.store_id = '0'
        folder.name = 'whatever'

        ur = folder.user_and_role.add()
        ur.role = OWNER
        ur.user.user_email = 'test1@aerofs.com'
        ur.user.first_name = 'first'
        ur.user.last_name = 'last'

        ur = folder.user_and_role.add()
        ur.role = EDITOR
        ur.user.user_email = 'test2@aerofs.com'
        ur.user.first_name = 'first'
        ur.user.last_name = 'last'

    def test_list_shared_folders(self):
        from modules.shared_folders.views import json_get_shared_folders

        request = testing.DummyRequest()
        request.params = {
            'sEcho': 'hoho',
            'iDisplayLength': 10,
            'iDisplayStart': 0
        }

        response = json_get_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)
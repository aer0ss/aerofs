from pyramid import testing
from mock import Mock
from aerofs_sp.gen.sp_pb2 import \
    ListOrganizationSharedFoldersReply, ListUserSharedFoldersReply
from aerofs_common._gen.common_pb2 import EDITOR, OWNER
from aerofs_sp.gen.sp_pb2 import ADMIN
from ..test_base import TestBase

class GetOrganizationSharedFoldersTest(TestBase):
    def setUp(self):
        self.setup_common()
        self._mock_list_organization_shared_folders()
        self._mock_list_user_shared_folders()

    def tearDown(self):
        testing.tearDown()

    def _mock_list_organization_shared_folders(self):
        reply = ListOrganizationSharedFoldersReply()
        reply.total_count = 2
        self._add_shared_folder(reply)
        self._add_shared_folder(reply)

        self.sp_rpc_stub.list_organization_shared_folders = Mock(return_value=reply)

    def _mock_list_user_shared_folders(self):
        reply = ListUserSharedFoldersReply()
        self._add_shared_folder(reply)
        self._add_shared_folder(reply)

        self.sp_rpc_stub.list_user_shared_folders = Mock(return_value=reply)

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

    def test_get_organization_shared_folders(self):
        from modules.shared_folders.views import \
            json_get_organization_shared_folders

        request = testing.DummyRequest()
        request.params = {
            'sEcho': 'hoho',
            'iDisplayLength': 10,
            'iDisplayStart': 0
        }
        request.session['username'] = 'test@email'
        request.session['group'] = ADMIN

        response = json_get_organization_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)

    def test_get_user_shared_folders(self):
        from modules.shared_folders.views import\
            json_get_user_shared_folders, URL_PARAM_USER

        request = testing.DummyRequest()
        request.params = {
            'sEcho': 'hoho',
            URL_PARAM_USER: 'some@email'
        }
        request.session['username'] = 'test@email'
        request.session['group'] = ADMIN

        response = json_get_user_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)
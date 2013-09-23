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

        # TestBase.setup_common() mocks global methods. Therefore, reload the
        # module under test to reset its referecnes to these methods, in case
        # the module has been loaded before by other test cases.
        # TODO (WW) a better way to do it?
        from web.views.shared_folders import shared_folders_view
        reload(shared_folders_view)

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

        # TODO (WW) use create_autospec? also see JsonAddSharedFolderPermTest
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

    def test_get_team_shared_folders(self):
        from web.views.shared_folders.shared_folders_view import \
            json_get_team_shared_folders

        request = self.create_dummy_request({
            'sEcho': 'hoho',
            'iDisplayLength': 10,
            'iDisplayStart': 0
        })
        request.session['username'] = 'test@email'
        request.session['group'] = ADMIN

        response = json_get_team_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)

    def test_get_user_shared_folders(self):
        from web.views.shared_folders.shared_folders_view import\
            json_get_user_shared_folders, URL_PARAM_USER

        request = self.create_dummy_request({
            'sEcho': 'hoho',
            URL_PARAM_USER: 'some@email'
        })
        request.session['username'] = 'test@email'
        request.session['group'] = ADMIN

        response = json_get_user_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)
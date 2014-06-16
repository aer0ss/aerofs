import unittest
from mock import Mock
from aerofs_sp.gen.sp_pb2 import \
    ListOrganizationSharedFoldersReply, ListSharedFoldersReply
from aerofs_common._gen.common_pb2 import WRITE, MANAGE
from aerofs_sp.gen.sp_pb2 import JOINED
from ..test_base import TestBase
from web import auth


class GetSharedFoldersTest(TestBase):
    def setUp(self):
        self.setup_common()
        self._mock_list_organization_shared_folders()
        self._mock_list_user_shared_folders()

        # TestBase.setup_common() mocks global methods. Therefore, reload the
        # module under test to reset its references to these methods, in case
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
        reply = ListSharedFoldersReply()
        self._add_shared_folder(reply)
        self._add_shared_folder(reply)

        # TODO (WW) use create_autospec? also see JsonAddSharedFolderPermTest
        self.sp_rpc_stub.list_user_shared_folders = Mock(return_value=reply)

    def _add_shared_folder(self, reply):
        folder = reply.shared_folder.add()

        folder.store_id = '0'
        folder.name = 'whatever'

        urs = folder.user_permissions_and_state.add()
        urs.permissions.permission.append(WRITE)
        urs.permissions.permission.append(MANAGE)
        urs.state = JOINED
        urs.user.user_email = 'test1@aerofs.com'
        urs.user.first_name = 'first'
        urs.user.last_name = 'last'

        urs = folder.user_permissions_and_state.add()
        urs.permissions.permission.append(WRITE)
        urs.state = JOINED
        urs.user.user_email = 'test2@aerofs.com'
        urs.user.first_name = 'first'
        urs.user.last_name = 'last'

    def test_get_org_shared_folders(self):
        from web.views.shared_folders.shared_folders_view import \
            json_get_org_shared_folders

        request = self.create_dummy_request({
            'sEcho': 'hoho',
            'iDisplayLength': 10,
            'iDisplayStart': 0
        })
        request.registry.settings["mako.directories"] = "web.views.shared_folders:templates"
        auth.is_admin = Mock(return_value=True)

        response = json_get_org_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)

    def test_get_user_shared_folders(self):
        from web.views.shared_folders.shared_folders_view import\
            json_get_user_shared_folders, URL_PARAM_USER

        request = self.create_dummy_request({
            'sEcho': 'hoho',
            URL_PARAM_USER: 'some@email'
        })
        request.registry.settings["mako.directories"] = "web.views.shared_folders:templates"
        auth.is_admin = Mock(return_value=True)

        response = json_get_user_shared_folders(request)
        self.assertEquals(len(response['aaData']), 2)

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

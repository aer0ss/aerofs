from pyramid import testing
from mock import Mock
from ..test_base import TestBase
from aerofs_sp.gen.sp_pb2 import ListOrganizationInvitedUsersReply

_USER_ID = 'test user id'

class UsersViewTest(TestBase):
    def setUp(self):
        self.setup_common()
        self.reply = ListOrganizationInvitedUsersReply()
        self.reply.user_id.append(_USER_ID)

        # TODO (WW) use create_autospec?
        self.sp_rpc_stub.list_organization_invited_users = \
            Mock(return_value=self.reply)

    def tearDown(self):
        testing.tearDown()

    def test_should_call_list_organization_invited_users(self):
        from modules.admin_panel.views import users_view
        ret = users_view(testing.DummyRequest())
        self.assertTrue(ret['invited_users'] == [_USER_ID])

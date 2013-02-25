from pyramid import testing
from mock import Mock
from ..test_base import TestBase

class UsersViewTest(TestBase):
    def setUp(self):
        self.setup_common()
        self.reply = Mock()
        self.sp_rpc_stub.list_organization_invited_users = \
            Mock(return_value=self.reply)

    def tearDown(self):
        testing.tearDown()

    def test_should_call_list_organization_invited_users(self):
        from modules.admin_panel.views import users_view
        ret = users_view(testing.DummyRequest())
        self.assertTrue(ret['invited_users'] is self.reply.user_id)

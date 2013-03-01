from pyramid import testing
from mock import Mock
from aerofs_sp.gen.sp_pb2 import ListUsersReply
from ..test_base import TestBase

class UserLookupTest(TestBase):
    def setUp(self):
        self.setup_common()
        self._mock_list_users_auth()

    def tearDown(self):
        testing.tearDown()

    def _mock_list_users_auth(self):
        reply = ListUsersReply()
        reply.total_count = 3
        reply.filtered_count = 3

        user = reply.users.add()
        user.user_email = "test1@awesome.com"
        user.first_name = "test1"
        user.last_name = "awesome"

        user = reply.users.add()
        user.user_email = "test2@awesome.com"
        user.first_name = "test2"
        user.last_name = "awesome"

        user = reply.users.add()
        user.user_email = "test3@awesome.com"
        user.first_name = "test3"
        user.last_name = "awesome"

        # TODO (WW) use create_autospec?
        self.sp_rpc_stub.list_users_auth = Mock(return_value=reply)

    def test_find_all_users_keys(self):
        from modules.admin_panel.views import json_user_lookup

        request = self.create_request({
            "searchTerm": "",
            "authLevel": "USER",
            "count": 10,
            "offset": 0
        })

        response = json_user_lookup(request)
        self.assertTrue(response.has_key("users"))

    def test_find_all_users_values(self):
        from modules.admin_panel.views import json_user_lookup

        request = self.create_request({
            "searchTerm": "",
            "authLevel": "USER",
            "count": 10,
            "offset": 0
        })

        response = json_user_lookup(request)
        emails = response["users"]

        self.assertEquals(3, len(emails))
        self.assertEquals("test1@awesome.com", emails[0])
        self.assertEquals("test2@awesome.com", emails[1])
        self.assertEquals("test3@awesome.com", emails[2])

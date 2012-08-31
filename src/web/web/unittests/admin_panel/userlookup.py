import unittest
from pyramid import testing
from mock import Mock
from aerofs_web import helper_functions
from aerofs.gen.sp_pb2 import SPServiceRpcStub

class PBUser(object):
    def __init__(self, email, firstname, lastname):
        self.user_email = email
        self.first_name = firstname
        self.last_name = lastname

class ListUsersReply(object):
    def __init__(self, users=[], total_count=0, filtered_count=0):
        self.users = users
        self.total_count = total_count
        self.filtered_count = filtered_count

class UserLookupTest(unittest.TestCase):
    def setUp(self):
        self.config = testing.setUp()
        reply = self._list_users_auth()
        self.stub = SPServiceRpcStub(None)
        self.stub.list_users_auth = Mock(return_value=reply)
        helper_functions.get_rpc_stub = Mock(return_value=self.stub)

    def tearDown(self):
        testing.tearDown()

    def _list_users_auth(self):
        users = [ PBUser("test1@awesome.com", "test1", "awesome"),
                  PBUser("test2@awesome.com", "test2", "awesome"),
                  PBUser("test3@awesome.com", "test3", "awesome") ]
        reply = ListUsersReply(users, 3, 3)
        return reply

    def test_find_all_users_keys(self):
        from modules.admin_panel.views import user_lookup_view
        request = testing.DummyRequest()
        request.params = {
            "searchTerm": "",
            "authLevel": "USER",
            "count": 10,
            "offset": 0
        }

        response = user_lookup_view(request)
        self.assertTrue(response.has_key("users"))

    def test_find_all_users_values(self):
        from modules.admin_panel.views import user_lookup_view
        request = testing.DummyRequest()
        request.params = {
            "searchTerm": "",
            "authLevel": "USER",
            "count": 10,
            "offset": 0
        }

        response = user_lookup_view(request)
        emails = response["users"]

        self.assertEquals(3, len(emails))
        self.assertEquals("test1@awesome.com", emails[0])
        self.assertEquals("test2@awesome.com", emails[1])
        self.assertEquals("test3@awesome.com", emails[2])

    def test_find_all_datatable_keys(self):
        from modules.admin_panel.views import user_datatable_view
        request = testing.DummyRequest()
        request.GET = {
            "sEcho": "1",
            "iDisplayLength": "10",
            "iDisplayStart": "0",
            "searchTerm": "",
            "authLevel": "USER"
        }

        response = user_datatable_view(request)
        self.assertTrue(response.has_key("sEcho"))
        self.assertTrue(response.has_key("iTotalRecords"))
        self.assertTrue(response.has_key("iTotalDisplayRecords"))
        self.assertTrue(response.has_key("aaData"))

        for entry in response["aaData"]:
            self.assertTrue(entry.has_key("email"))
            self.assertTrue(entry.has_key("action"))

    def test_find_all_datatable_values(self):
        from modules.admin_panel.views import user_datatable_view
        request = testing.DummyRequest()
        request.GET = {
            "sEcho": "1",
            "iDisplayLength": "10",
            "iDisplayStart": "0",
            "searchTerm": "",
            "authLevel": "USER"
        }

        response = user_datatable_view(request)
        echo = response["sEcho"]
        total_records = response["iTotalRecords"]
        display_records = response["iTotalDisplayRecords"]
        table_entries = response["aaData"]

        self.assertEquals("1", echo)
        self.assertEquals(3, total_records)
        self.assertEquals(3, display_records)

        self.assertEquals("test1@awesome.com", table_entries[0]["email"])

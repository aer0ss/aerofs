import unittest
from pyramid import testing
from mock import Mock
from ..test_base import TestBase
from markupsafe import Markup
from aerofs_sp.gen.sp_pb2 import ListOrganizationInvitedUsersReply, \
    ListOrganizationMembersReply

_USER_ID = 'test user id'


class OrganizationUsersViewTest(TestBase):
    def setUp(self):
        self.setup_common()
        from web.views.org_users import org_users_view
        reload(org_users_view)

        self.reply = ListOrganizationMembersReply()
        self.reply.total_count = 1
        ul = self.reply.user_and_level.add()
        ul.level = 1
        ul.user.first_name  = 'test'
        ul.user.last_name = 'test'
        ul.user.user_email = 'test@email.com'
        ul.user.two_factor_enforced = True

        self.sp_rpc_stub.list_organization_members = \
            Mock(return_value=self.reply)

    def test_should_list_organization_users_and_return_correct_data(self):
        from web.views.org_users.org_users_view import json_list_org_users
        res = json_list_org_users(testing.DummyRequest())

        output = res['data']
        expected = [{
            "first_name": 'test',
            "last_name": 'test',
            "email": Markup(u'test@email.com'),
            "has_two_factor": True,
            "is_publisher": False,
            "is_admin": True,
            "name": Markup(u'test test (test@email.com)')
        }]
        self.assertEqual(output, expected)
        self.assertEqual(res['total'], 1)
        self.assertEqual(res['pagination_limit'], 20)


class InvitedUsersViewTest(TestBase):
    def setUp(self):
        self.setup_common()
        self.reply = ListOrganizationInvitedUsersReply()
        self.reply.user_id.append(_USER_ID)

        # TODO (WW) use create_autospec? also see JsonAddSharedFolderPermTest
        self.sp_rpc_stub.list_organization_invited_users = \
            Mock(return_value=self.reply)

    def test_should_call_list_organization_invited_users(self):
        from web.views.org_users.org_users_view import json_list_org_invitees
        ret = json_list_org_invitees(testing.DummyRequest())
        print ret['invitees']
        self.assertTrue(ret['invitees'] == [{'email': _USER_ID}])

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

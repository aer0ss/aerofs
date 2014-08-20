import unittest
from pyramid import testing
from mock import Mock
from ..test_base import TestBase
from aerofs_sp.gen.sp_pb2 import ListOrganizationInvitedUsersReply

_USER_ID = 'test user id'


class TeamMembersViewTest(TestBase):
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

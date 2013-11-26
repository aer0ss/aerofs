import unittest
from aerofs_sp.gen.sp_pb2 import PBSharedFolder, JOINED
from aerofs_common._gen.common_pb2 import WRITE, MANAGE

BASE_USER_ID = 'hahaha@ahaha'

# Some bizzare unicode names
NAME1 = u'1\U00008000'
NAME2 = u'2\u1234'
NAME3 = u'3\u4321'
NAME4 = u'4\u8652'
NAME5 = u'5\xac'
NAME6 = u'6\x12'

class RenderSharedFolderUsersTest(unittest.TestCase):

    def _render_shared_folder_users(self, first_names):
        return self._render_shared_folder_users_with_session_user(first_names,
            "test@test")

    def _render_shared_folder_users_with_session_user(self, first_names, session_user):
        from web.views.shared_folders.shared_folders_view import _render_shared_folder_users

        return _render_shared_folder_users(self._compose_user_list(first_names),
            session_user)

    def _compose_user_list(self, first_names):
        user_permissions_and_state_list = []
        for i in range(len(first_names)):
            user_permissions_and_state = PBSharedFolder.PBUserPermissionsAndState()
            user_permissions_and_state.user.user_email = self._get_user_id(i)
            user_permissions_and_state.user.first_name = first_names[i]
            user_permissions_and_state.user.last_name = ""
            user_permissions_and_state.permissions.permission.append(WRITE)
            user_permissions_and_state.permissions.permission.append(MANAGE)
            user_permissions_and_state.state = JOINED
            user_permissions_and_state_list.append(user_permissions_and_state)

        return user_permissions_and_state_list

    def _get_user_id(self, index):
        return BASE_USER_ID + str(index)

    def test_empty_user_list(self):

        str = self._render_shared_folder_users([])
        self.assertEquals(str, '')

    def test_one_user(self):
        str = self._render_shared_folder_users([NAME1])
        self.assertEquals(str, NAME1 + " only")

    def test_one_user_with_session_user(self):
        str = self._render_shared_folder_users_with_session_user(["hahahaha"],
            self._get_user_id(0))
        self.assertEquals(str, "me only")

    def test_two_users_with_unicode(self):
        str = self._render_shared_folder_users([NAME1, NAME2])
        self.assertEquals(str, NAME1 + " and " + NAME2)

    def test_three_users(self):
        str = self._render_shared_folder_users([NAME1, NAME2, NAME3])
        self.assertEquals(str,
            NAME1 + ", " + NAME2 + ", and " + NAME3)

    def test_three_users_with_session_user(self):
        """
        The method under test should always place "me" last.
        """
        str = self._render_shared_folder_users_with_session_user(
            [NAME1, NAME2, NAME3], self._get_user_id(1))
        self.assertEquals(str, NAME1 + ", " + NAME3 + ", and me")

    def test_four_users(self):
        str = self._render_shared_folder_users(
            [NAME1, NAME2, NAME3, NAME4])
        self.assertEquals(str, NAME1 + ", " + NAME2 + ", " +
                               NAME3 + ", and " + NAME4)

    def test_four_users_with_session_user(self):
        str = self._render_shared_folder_users_with_session_user(
            [NAME1, NAME2, NAME3, NAME4],
            self._get_user_id(2))
        self.assertEquals(str, NAME1 + ", " + NAME2 + ", " +
                               NAME4 + ", and me")

    def test_six_users_with_session_user(self):
        str = self._render_shared_folder_users_with_session_user(
            [NAME1, NAME2, NAME3, NAME4, NAME4, NAME6], self._get_user_id(0))
        self.assertEquals(str, NAME2 + ", " + NAME3 + ", " +
                               NAME4 + ", and 3 others")

    def test_should_escape_html(self):
        first_name = '<&>'
        str = self._render_shared_folder_users([first_name])
        self.assertEquals(str, "&lt;&amp;&gt; only")

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

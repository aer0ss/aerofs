import unittest
from aerofs_sp.gen.sp_pb2 import PBSharedFolder
from aerofs_common._gen.common_pb2 import OWNER

BASE_USER_ID = 'hahaha@ahaha'

class RenderSharedFolderUsersTest(unittest.TestCase):

    def _render_shared_folder_users(self, first_names):
        return self._render_shared_folder_users_with_session_user(first_names,
            "test@test")

    def _render_shared_folder_users_with_session_user(self, first_names, session_user):
        from modules.shared_folders.shared_folders_view import _render_shared_folder_users

        return _render_shared_folder_users(self._compose_user_list(first_names),
            session_user)

    def _compose_user_list(self, first_names):
        user_and_role_list = []
        for i in range(len(first_names)):
            user_and_role = PBSharedFolder.PBUserAndRole()
            user_and_role.user.user_email = self._get_user_id(i)
            user_and_role.user.first_name = first_names[i]
            user_and_role.user.last_name = ""
            user_and_role.role = OWNER
            user_and_role_list.append(user_and_role)

        return user_and_role_list

    def _get_user_id(self, index):
        return BASE_USER_ID + str(index)

    def test_empty_user_list(self):

        str = self._render_shared_folder_users([])
        self.assertEquals(str, '')

    def test_one_user(self):
        first_name = 'hahaha'
        str = self._render_shared_folder_users([first_name])
        self.assertEquals(str, first_name + " only")

    def test_one_user_with_session_user(self):
        str = self._render_shared_folder_users_with_session_user(["hahahaha"],
            self._get_user_id(0))
        self.assertEquals(str, "me only")

    def test_two_users(self):
        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        str = self._render_shared_folder_users([first_name1, first_name2])
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_two_users_with_long_name_at_first(self):
        first_name1 = self._long_name()
        first_name2 = 'hohoho'

        str = self._render_shared_folder_users([first_name1, first_name2])
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_two_users_with_long_name_at_second(self):
        first_name1 = 'hohoho'
        first_name2 = self._long_name()

        str = self._render_shared_folder_users([first_name1, first_name2])
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_three_users(self):
        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'
        str = self._render_shared_folder_users([first_name1, first_name2, first_name3])
        self.assertEquals(str,
            first_name1 + ", " + first_name2 + ", and " + first_name3)

    def test_three_users_with_long_name_at_first(self):
        first_name1 = self._long_name()
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'

        str = self._render_shared_folder_users([first_name1, first_name2, first_name3])
        self.assertEquals(str, first_name1 + " and 2 others")

    def test_three_users_with_long_name_at_second(self):
        first_name1 = 'hohoho'
        first_name2 = self._long_name()
        first_name3 = 'xixixi'

        str = self._render_shared_folder_users([first_name1, first_name2, first_name3])
        self.assertEquals(str, first_name1 + " and 2 others")

    def test_three_user_with_long_name_at_third(self):
        first_name1 = 'hohoho'
        first_name2 = 'xixixi'
        first_name3 = self._long_name()

        str = self._render_shared_folder_users([first_name1, first_name2, first_name3])
        self.assertEquals(str, first_name1 + ", " + first_name2 + ", and " + first_name3)

    def test_three_users_with_session_user(self):
        """
        The method under test should always place "me" last.
        """
        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'
        str = self._render_shared_folder_users_with_session_user(
            [first_name1, first_name2, first_name3], self._get_user_id(1))
        self.assertEquals(str, first_name1 + ", " + first_name3 + ", and me")

    def test_four_users(self):
        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'
        first_name4 = 'qqqqqq'

        str = self._render_shared_folder_users(
            [first_name1, first_name2, first_name3, first_name4])
        self.assertEquals(str,
            first_name1 + ", " + first_name2 + ", " + first_name3 +
            ", and " + first_name4)

    def test_should_escape_html(self):
        first_name = '<&>'
        str = self._render_shared_folder_users([first_name])
        self.assertEquals(str, "&lt;&amp;&gt; only")

    def _long_name(self):
        return ''.join(['a' for num in xrange(1000)])
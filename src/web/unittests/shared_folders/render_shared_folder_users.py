import unittest
from aerofs_sp.gen.sp_pb2 import ListSharedFoldersReply
from aerofs_common._gen.common_pb2 import OWNER

class RenderSharedFolderUsersTest(unittest.TestCase):

    def _compose_user_list(self, first_names):
        user_and_role_list = []
        for first_name in first_names:
            user_and_role = ListSharedFoldersReply.PBSharedFolder.PBUserAndRole()
            user_and_role.user.user_email = 'hahaha@ahaha'
            user_and_role.user.first_name = first_name
            user_and_role.user.last_name = ""
            user_and_role.role = OWNER
            user_and_role_list.append(user_and_role)

        return user_and_role_list

    def test_empty_user_list(self):
        from modules.shared_folders.views import render_shared_folder_users

        str = render_shared_folder_users(self._compose_user_list([]))
        self.assertEquals(str, '')

    def test_one_user(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name = 'hahaha'
        str = render_shared_folder_users(self._compose_user_list([first_name]))
        self.assertEquals(str, first_name)

    def test_two_users(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2]))
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_two_users_with_long_name_at_first(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = self._long_name()
        first_name2 = 'hohoho'

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2]))
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_two_users_with_long_name_at_second(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hohoho'
        first_name2 = self._long_name()

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2]))
        self.assertEquals(str, first_name1 + " and " + first_name2)

    def test_three_users(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'
        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2, first_name3]))
        self.assertEquals(str,
            first_name1 + ", " + first_name2 + ", and " + first_name3)

    def test_three_users_with_long_name_at_first(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = self._long_name()
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2, first_name3]))
        self.assertEquals(str, first_name1 + " and 2 others")

    def test_three_users_with_long_name_at_second(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hohoho'
        first_name2 = self._long_name()
        first_name3 = 'xixixi'

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2, first_name3]))
        self.assertEquals(str, first_name1 + " and 2 others")

    def test_three_user_with_long_name_at_third(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hohoho'
        first_name2 = 'xixixi'
        first_name3 = self._long_name()

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2, first_name3]))
        self.assertEquals(str,
            first_name1 + ", " + first_name2 + ", and " + first_name3)

    def test_four_users(self):
        from modules.shared_folders.views import render_shared_folder_users

        first_name1 = 'hahaha'
        first_name2 = 'hohoho'
        first_name3 = 'xixixi'
        first_name4 = 'qqqqqq'

        str = render_shared_folder_users(self._compose_user_list(
            [first_name1, first_name2, first_name3, first_name4]))
        self.assertEquals(str,
            first_name1 + ", " + first_name2 + ", " + first_name3 +
            ", and " + first_name4)

    def _long_name(self):
        return ''.join(['a' for num in xrange(1000)])
import unittest
from ..test_base import TestBase

class JsonSharedFolderPermTest(TestBase):
    def setUp(self):
        self.setup_common()

        # TestBase.setup_common() mocks global methods. Therefore, reload the
        # module under test to reset its referecnes to these methods, in case
        # the module has been loaded before by other test cases.
        # TODO (WW) a better way to do it?
        from web.views.shared_folders import shared_folders_view
        reload(shared_folders_view)

    def test_add_shared_folder_perm__should_call_sp_with_correct_signature(self):
        """
        The method under test shouldn't throw if it calls SP service stub with
        correct parameters
        """
        from web.views.shared_folders.shared_folders_view import \
            json_add_shared_folder_perm

        params = {
            'user_id': 'test_user_id',
            'store_id': 'QURGDLXVTIGZHJ5JQMOB4DIVEM======',
            'folder_name': 'test_folder_name',
            'permissions': [],
            'suppress_sharing_rules_warnings': True
        }
        request = self.create_dummy_request(json_body=params)

        json_add_shared_folder_perm(request)

    def test_set_shared_folder_perm__should_call_sp_with_correct_signature(self):
        """
        The method under test shouldn't throw if it calls SP service stub with
        correct parameters
        """
        from web.views.shared_folders.shared_folders_view import \
            json_set_shared_folder_perm

        params = {
            'user_id': 'test_user_id',
            'store_id': 'QURGDLXVTIGZHJ5JQMOB4DIVEM======',
            'permissions': [],
            'suppress_sharing_rules_warnings': True
        }
        request = self.create_dummy_request(json_body=params)

        json_set_shared_folder_perm(request)

    def test_delete_shared_folder_perm__should_call_sp_with_correct_signature(self):
        """
        The method under test shouldn't throw if it calls SP service stub with
        correct parameters
        """
        from web.views.shared_folders.shared_folders_view import \
            json_delete_shared_folder_perm

        params = {
            'user_id': 'test_user_id',
            'store_id': 'QURGDLXVTIGZHJ5JQMOB4DIVEM======',
        }
        request = self.create_dummy_request(json_body=params)

        json_delete_shared_folder_perm(request)

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

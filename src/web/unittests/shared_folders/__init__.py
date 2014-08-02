import unittest

def test_suite():
    # import all test submodules
    import unittests.shared_folders.json_get_shared_folders_test
    import unittests.shared_folders.json_shared_folder_perm_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.shared_folders.json_get_shared_folders_test.test_suite())
    suite.addTest(unittests.shared_folders.json_shared_folder_perm_test.test_suite())

    return suite

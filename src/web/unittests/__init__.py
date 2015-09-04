import unittest


def test_suite():
    # import all test modules
    import unittests.accept
    import unittests.devices
    import unittests.login
    import unittests.misc
    import unittests.shared_folders
    import unittests.org_users
    import unittests.org_settings
    import unittests.password_reset
    import unittests.settings
    import unittests.oauth

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.accept.test_suite())
    suite.addTest(unittests.devices.test_suite())
    suite.addTest(unittests.login.test_suite())
    suite.addTest(unittests.misc.test_suite())
    suite.addTest(unittests.password_reset.test_suite())
    suite.addTest(unittests.shared_folders.test_suite())
    suite.addTest(unittests.org_users.test_suite())
    suite.addTest(unittests.org_settings.test_suite())
    suite.addTest(unittests.settings.test_suite())
    suite.addTest(unittests.oauth.test_suite())

    return suite

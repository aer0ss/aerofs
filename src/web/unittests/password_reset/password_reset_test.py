import unittest
from unittests.test_base import TestBase


class PasswordResetTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test__password_reset_should_fail_bad_params(self):
        """
        invalid parameters should fail nicely
        """
        from web.views.password_reset.password_reset_view import password_reset

        request = self.create_dummy_request({})
        request.method = 'POST'
        result = password_reset(request)
        self.assertFalse(result.get("success"))

    def test__password_reset_should_accept_password(self):
        """
        This test verifies that password_reset likes my suggested password
        """
        from web.views.password_reset.password_reset_view import password_reset

        request = self.create_dummy_request({
            "token": "himom",
            "user_id": "a@b.c",
            "password": "This is a password",
        })

        request.host_url = "hahahhoho"
        request.method = 'POST'

        result = password_reset(request)
        self.assertEqual(result['user_id'], 'a@b.c')
        self.assertEqual(result['success'], True)
        self.assertEqual(result['valid_password'], True)

    def test__password_reset_should_fail_get(self):
        """
        This test verifies that password_reset doesn't like GET
        """
        from web.views.password_reset.password_reset_view import password_reset

        request = self.create_dummy_request({
            "token": "himom",
            "user_id": "a@b.c",
            "password": "This is a password",
        })

        request.host_url = "hahahhoho"
        request.method = 'GET'

        result = password_reset(request)
        self.assertFalse(result.get("success"))

    def test__password_reset_should_handle_missing_password(self):
        """
        This test verifies that password_reset can handle a request without a password member.
        """
        from web.views.password_reset.password_reset_view import password_reset

        request = self.create_dummy_request({
            "token": "himom",
            "user_id": "a@b.c",
        })

        request.host_url = "hahahhoho"
        request.method = 'POST'

        result = password_reset(request)
        self.assertFalse(result.get("success"))


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

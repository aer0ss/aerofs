from unittests.test_base import TestBase
from web.util import is_private_deployment
from web.views.login.login_view import URL_PARAM_NEXT, resolve_next_url, _is_external_cred_enabled, _format_password


class LoginTest(TestBase):

    def test_resolve_next_url_should_prefix_with_host_url(self):
        """
        This test verifies that resolve_next_url() adds the host URL to the next
        parameter as a security measure (see the method for more info).
        """

        request = self.create_dummy_request({
            URL_PARAM_NEXT: "cnn.com"
        })

        request.host_url = "hahahhoho"

        self.assertEqual(resolve_next_url(request), "hahahhohocnn.com")

    def test_internal_should_be_default(self):
        """
        This test verifies that if internal_email_pattern is None, we treat all
        addresses as internal.
        """
        settings = {"config.loader.is_private_deployment": "true",
                    "internal_email_pattern": None,
                    "lib.authenticator": "external_credential" }

        self.assertTrue(is_private_deployment(settings))
        self.assertTrue(_is_external_cred_enabled(settings))
        self._assert_internal(settings, "joe@example.com")
        self._assert_internal(settings, "a@b.c")

    def test_internal_matches(self):
        """
        This test verifies that if internal_email_pattern is .*, we treat all
        addresses as internal.
        """
        settings = {"config.loader.is_private_deployment": "true",
                    "internal_email_pattern": ".*",
                    "lib.authenticator": "external_credential" }

        self._assert_internal(settings, "joe@example.com")
        self._assert_internal(settings, "a@b.c")

    def test_external_matches(self):
        """
        This test verifies that internal_email_pattern return scrypt'ed passwords
        for external addresses.
        """
        settings = {"config.loader.is_private_deployment": "true",
                    "internal_email_pattern": ".example.com",
                    "lib.authenticator": "external_credential"}

        self._assert_internal(settings, "joe@example.com")
        self._assert_internal(settings, "blah@example.com")
        self._assert_external(settings, "a@b.c")

    def _assert_internal(self, settings, username):
        """
        Internal username means it should be cleartext, not scrypt'ed.
        """
        passwd = "hellosecret"
        self.assertEqual(passwd, _format_password(settings, passwd, username))

    def _assert_external(self, settings, username):
        """
        Internal username means it should be scrypted: not equal to the input string, and 64 characters long.
        """
        passwd = "hellosecret"
        self.assertNotEqual(passwd, _format_password(settings, passwd, username))
        self.assertEqual(_format_password(settings, passwd, username).__len__(), 64)

import unittest
from unittests.test_base import TestBase
from web.util import is_private_deployment
from web.login_util import URL_PARAM_NEXT, resolve_next_url
from web.views.login.login_view import _is_external_cred_enabled, _format_password


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

        self.assertEqual(resolve_next_url(request, 'default'), "hahahhoho/cnn.com")

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

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

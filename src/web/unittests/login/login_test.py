import unittest
from unittests.test_base import TestBase
from web.login_util import URL_PARAM_NEXT, resolve_next_url


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

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

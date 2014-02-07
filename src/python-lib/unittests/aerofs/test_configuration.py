import unittest
import requests
from requests import Response
from aerofs_common.configuration import Configuration

class TestConfiguration(unittest.TestCase):
    def test_fetch_should_exclude_browser_key(self):
        """
        @note this test covers: 1. server browser key is excluded, and
        2. unicode parsing is working because if it wasn't, the response
        won't match our expected output
        """
        self._mock_response('server.browser.key=topsecret\n'
                            'yet.another.property=public')
        self._verify_fetch_result({'yet.another.property': 'public'})

    def _mock_response(self, content):
        res = Response()
        res.status_code = 200
        res._content = content
        requests.get = lambda url: res

    def _verify_fetch_result(self, expected):
        config = Configuration("http://dummy:5434").server_properties()
        self.assertEqual(config, expected)

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

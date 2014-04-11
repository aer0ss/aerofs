
import unittest
import requests
import time
from mock import Mock
from web import version

now = 10000
# The timeout of the version value cache plus one
TIMEOUT = 61


class Resp:
    ok = None
    text = None
    status_code = None

    def __init__(self, ok, text):
        self.ok = ok
        self.status_code = 123
        self.text = text


class VersionTest(unittest.TestCase):
    def test_should_parse_version_string(self):
        requests.get = Mock(return_value=Resp(True, "Version=333"))

        # Advance the clock by TIMEOUT to force a cache refresh
        self.assertEqual(self.invoke(TIMEOUT), '333')

    def test_should_use_cached_value(self):
        # Make sure the cached version is 333
        self.test_should_parse_version_string()

        requests.get = Mock(return_value=Resp(True, "Version=444"))

        self.assertEqual(self.invoke(1), '333')

    def test_should_refresh_after_one_min(self):
        # Make sure the cached version is 333
        self.test_should_parse_version_string()

        requests.get = Mock(return_value=Resp(True, "Version=555"))

        self.assertEqual(self.invoke(TIMEOUT), '555')

    def test_should_use_cached_value_on_server_error(self):
        # Make sure the cached version is 333
        self.test_should_parse_version_string()

        requests.get = Mock(return_value=Resp(False, ""))

        self.assertEqual(self.invoke(TIMEOUT), '333')

    def test_should_throw_if_requests_throws(self):
        requests.get = Mock(side_effect=IOError)

        try:
            # Use TIMEOUT to force a cache refresh
            self.invoke(TIMEOUT)
            self.fail()
        except IOError:
            pass

    def test_initial_invoke_should_throw_on_server_error(self):
        requests.get = Mock(return_value=Resp(False, ""))
        version._public_version = None

        try:
            # Use TIMEOUT to force a cache refresh
            self.invoke(TIMEOUT)
            self.fail()
        except requests.RequestException:
            pass

    def invoke(self, advance):
        global now
        now = now + advance
        time.time = Mock(return_value=now)

        v = version.get_public_version({'installer.prefix': ''})
        return v


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

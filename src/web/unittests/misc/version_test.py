
import unittest
from web import version


class VersionTest(unittest.TestCase):
    def test_should_parse_version_string(self):
        # Doing white box testing because I don't know how to properly mock reading a file
        self.assertEqual(version._parse_version("Version=1.2.3"), "1.2.3")


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

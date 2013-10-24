import unittest
from web.views.setup.setup import _get_default_support_email

class SetupTest(unittest.TestCase):

    def test_get_default_support_email__should_work_as_expected(self):
        self._verify_default_support_email('', 'localhost')
        self._verify_default_support_email('a', 'a')
        self._verify_default_support_email('haha', 'haha')
        self._verify_default_support_email('.a', '.a')
        self._verify_default_support_email('.haha', '.haha')
        self._verify_default_support_email('haha.', 'haha.')
        self._verify_default_support_email('.haha.', '.haha.')
        self._verify_default_support_email('.haha.com', '.haha.com')
        self._verify_default_support_email('haha.com', 'com')
        self._verify_default_support_email('aaa.bbb.com', 'bbb.com')
        self._verify_default_support_email('.aaa.bbb.com', '.aaa.bbb.com')
        self._verify_default_support_email('aaa.bbb.com.', 'bbb.com.')
        self._verify_default_support_email('...', '...')

    def _verify_default_support_email(self, hostname, expected):
        self.assertEqual(_get_default_support_email(hostname),
                         'support@' + expected)
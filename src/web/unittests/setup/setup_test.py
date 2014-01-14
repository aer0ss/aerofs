# TODO (WW) move this file to the maintenance package to be consistent with
# the file under test.

import unittest
from pyramid.httpexceptions import HTTPBadRequest
from unittests.test_base import TestBase
from web.views.maintenance.setup_view import _get_default_support_email, json_setup_hostname

class SetupTest(TestBase):

    def test__get_default_support_email__should_work_as_expected(self):
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

    def test__json_setup_hostname__should_disallow_invalid_ip(self):
        try:
            self._call_setup_hostname('666.666.666.666')
            self.fail()
        except HTTPBadRequest:
            pass

    def test__json_setup_hostname__should_disallow_localhost(self):
        try:
            self._call_setup_hostname('localhost')
            self.fail()
        except HTTPBadRequest:
            pass

    def _call_setup_hostname(self, hostname):
        json_setup_hostname(self.create_dummy_request({
            'base.host.unified': hostname
        }))

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

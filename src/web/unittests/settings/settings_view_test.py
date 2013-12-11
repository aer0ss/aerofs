import unittest
from mock import Mock
from ..test_base import TestBase
import requests

class SettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test__json_json_delete_access_token_should_accept_valid_tokens(self):
        self._call('4547210d34774ba6b235ba9bd9fea8c5')

    def test__json_json_delete_access_token_should_reject_invalid_tokens(self):
        try:
            self._call('123')
            self.fail()
        except:
            pass

        try:
            self._call('../clients')
            self.fail()
        except:
            pass

    def _call(self, token):
        requests.delete = Mock()
        from web.views.settings.settings_view import json_delete_access_token
        request = self.create_dummy_request({
            'access_token': token
        })
        json_delete_access_token(request)


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

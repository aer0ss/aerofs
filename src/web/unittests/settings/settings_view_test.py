import unittest
from mock import Mock, patch
from ..test_base import TestBase
import requests

import web.oauth
from web.views.settings.access_tokens_view import json_delete_access_token

OAUTH_SERVER_URL = "http://localhost:8700"

class SettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def _get_test_bifrost_client(self):
        return web.oauth.DelegatedBifrostClient(OAUTH_SERVER_URL,
                deployment_secret='1234567890123456789012',
                delegated_user='test@aerofs.com',
                service_name='web')

    @patch('web.views.settings.access_tokens_view.get_bifrost_client')
    def test__json_json_delete_access_token_should_accept_valid_tokens(self, mock_client):
        mock_client.return_value = self._get_test_bifrost_client()
        self._call('4547210d34774ba6b235ba9bd9fea8c5')

    @patch('web.views.settings.access_tokens_view.get_bifrost_client')
    def test__json_json_delete_access_token_should_reject_invalid_tokens(self, mock_client):
        mock_client.return_value = self._get_test_bifrost_client()
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
        request = self.create_dummy_request({
            'access_token': token
        })
        json_delete_access_token(request)


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

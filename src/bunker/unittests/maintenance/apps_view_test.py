import unittest
from mock import Mock, patch
from ..test_base import TestBase
import requests

from web.oauth import PrivilegedBifrostClient
from web.views.maintenance.apps_view import json_delete_app

OAUTH_SERVER_URL = "http://localhost:8700"

class AppsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def _get_test_bifrost_client(self):
        return PrivilegedBifrostClient(OAUTH_SERVER_URL,
                deployment_secret='1234567890123456789012',
                service_name='web')

    @patch('web.views.maintenance.apps_view.get_privileged_bifrost_client')
    def test__json_delete_app_should_accept_valid_client_ids(self, get_test_bifrost_client):
        get_test_bifrost_client.return_value = self._get_test_bifrost_client()
        self._call('4547210d-3477-4ba6-b235-ba9bd9fea8c5')

    @patch('web.views.maintenance.apps_view.get_privileged_bifrost_client')
    def test__json_delete_app_should_reject_invalid_client_ids(self, get_test_bifrost_client):
        get_test_bifrost_client.return_value = self._get_test_bifrost_client()
        try:
            self._call('123')
            self.fail()
        except:
            pass

        try:
            self._call('../tokens')
            self.fail()
        except:
            pass

    def _call(self, client_id):
        requests.delete = Mock()
        request = self.create_dummy_request({
            'client_id': client_id
        })
        json_delete_app(request)


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

import unittest
from mock import Mock
from ..test_base import TestBase
import requests

class AppsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test__json_delete_app_should_accept_valid_client_ids(self):
        self._call('4547210d-3477-4ba6-b235-ba9bd9fea8c5')

    def test__json_delete_app_should_reject_invalid_client_ids(self):
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
        from web.views.maintenance.apps_view import json_delete_app
        request = self.create_dummy_request({
            'client_id': client_id
        })
        json_delete_app(request)


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

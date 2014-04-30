import unittest
from mock import Mock
from ..test_base import TestBase
from pyramid.httpexceptions import HTTPOk

class MonitoringViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test__json_regenerate_monitoring_cred(self):
        self._call()

    def _call(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.monitoring_view import json_regenerate_monitoring_cred
        request = self.create_dummy_request({
            'monitoring_password': 'dummy',
        })
        request.registry.settings['deployment.config_server_uri'] = 'http://localhost:5434/'

        http_result = json_regenerate_monitoring_cred(request)
        assert isinstance(http_result, HTTPOk)
        maintenance_util.get_conf_client.set_external_property.assert_called_once()


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

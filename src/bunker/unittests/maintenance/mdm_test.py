import unittest
from mock import Mock
from ..test_base import TestBase
from pyramid.httpexceptions import HTTPOk, HTTPBadRequest
from webob.multidict import MultiDict


class MdmViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test__json_set_mdm(self):
        self._call_no_cidr()
        self._call_with_wrong_cidr()
        self._call_one_wrong_cidr()
        self._call_successful()
        self._call_successful_multi()

    def _call_no_cidr(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.mdm_view import json_set_mobile_device_management

        #won't make any change unless have a CIDR_ form attribute
        request = self.create_dummy_request(MultiDict([
            ('enabled', 'true'),
            ('garbage', '1.2.3.4/12'),
        ]))

        try:
            json_set_mobile_device_management(request)
            self.fail()
        except HTTPBadRequest:
            pass
        assert not maintenance_util.get_conf_client.set_external_property.called

    def _call_with_wrong_cidr(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.mdm_view import json_set_mobile_device_management
        #will fail if CIDR input is invalid
        request = self.create_dummy_request(MultiDict([
            ('enabled', 'true'),
            ('CIDR','wrong'),
        ]))

        try:
            json_set_mobile_device_management(request)
            self.fail()
        except HTTPBadRequest:
            pass
        assert not maintenance_util.get_conf_client.set_external_property.called

    def _call_one_wrong_cidr(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.mdm_view import json_set_mobile_device_management
        #will fail if any of the CIDR input are invalid
        request = self.create_dummy_request(MultiDict([
            ('enabled', 'true'),
            ('CIDR', '1.2.3.4/12'),
            ('CIDR', 'wrong'),
        ]))

        try:
            json_set_mobile_device_management(request)
            self.fail()
        except HTTPBadRequest:
            pass
        assert not maintenance_util.get_conf_client.set_external_property.called

    def _call_successful(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.mdm_view import json_set_mobile_device_management
        #works for well formed CIDR blocks
        request = self.create_dummy_request(MultiDict([
            ('enabled', 'true'),
            ('CIDR', '1.2.3.4/12'),
        ]))
        request.registry.settings['deployment.config_server_uri'] = 'http://localhost:5434/'

        http_result = json_set_mobile_device_management(request)

        assert isinstance(http_result, HTTPOk)
        maintenance_util.get_conf_client.set_external_property.assert_called_once()

    def _call_successful_multi(self):
        from web.views.maintenance import maintenance_util

        maintenance_util.get_conf_client = Mock()
        maintenance_util.get_conf = Mock()

        from web.views.maintenance.mdm_view import json_set_mobile_device_management
        #also works for multiple cidr fields
        request = self.create_dummy_request(params=MultiDict([
            ('enabled', 'true'),
            ('CIDR', '1.2.3.4/12'),
            ('CIDR', '5.6.7.8/24'),
        ]))
        request.registry.settings['deployment.config_server_uri'] = 'http://localhost:5434/'

        http_result = json_set_mobile_device_management(request)

        assert isinstance(http_result, HTTPOk)
        maintenance_util.get_conf_client.set_external_property.assert_called_once()


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

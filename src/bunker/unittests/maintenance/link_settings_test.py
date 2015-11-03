import unittest
from mock import Mock, patch
from ..test_base import TestBase

CONFIG_SERVER_URL = 'http://localhost:5434/'

class LinkSettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    @patch('web.views.maintenance.link_settings_view.get_conf')
    def test__link_settings(self, mock_get):
        mock_get.return_value.get = Mock()
        from web.views.maintenance.link_settings_view import link_settings_view

        request = self.create_dummy_request({})
        request.registry.settings['deployment.config_server_uri'] = CONFIG_SERVER_URL

        http_result = link_settings_view(request)

        assert http_result['links_require_login'] is False
        mock_get.assert_called_once_with(request)
        mock_get.return_value.get.assert_called_once_with("links_require_login.enabled", False)


    @patch('web.views.maintenance.link_settings_view.get_conf_client')
    def test__json_set_require_login_to_true(self, mock_client):
        mock_client.return_value.set_external_property = Mock()

        from web.views.maintenance.link_settings_view import json_set_require_login_post
        request = self.create_dummy_request({
            'links_require_login': 'true'
        })
        request.registry.settings['deployment.config_server_uri'] = CONFIG_SERVER_URL

        http_result = json_set_require_login_post(request)
        assert http_result == {}
        mock_client.assert_called_once_with(request)
        mock_client.return_value.set_external_property.assert_called_once_with('links_require_login_enabled', True)

    @patch('web.views.maintenance.link_settings_view.get_conf_client')
    def test__json_set_require_login_to_false(self, mock_client):
        mock_client.return_value.set_external_property = Mock()

        from web.views.maintenance.link_settings_view import json_set_require_login_post
        request = self.create_dummy_request({
            'links_require_login': 'false'
        })
        request.registry.settings['deployment.config_server_uri'] = CONFIG_SERVER_URL

        http_result = json_set_require_login_post(request)
        assert http_result == {}
        mock_client.assert_called_once_with(request)
        mock_client.return_value.set_external_property.assert_called_once_with('links_require_login_enabled', False)


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)
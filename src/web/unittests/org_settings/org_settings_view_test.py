import unittest
from mock import Mock
from ..test_base import TestBase


class OrgSettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

        # TestBase.setup_common() mocks global methods. Therefore, reload the
        # module under test to reset its references to these methods, in case
        # the module has been loaded before by other test cases.
        # TODO (WW) a better way to do it?
        from web.views.org_settings import org_settings_view
        reload(org_settings_view)

        # Create spy methods. Do not use mock so the RPC stub can validate
        # parameters passed to these methods.
        self.sp_rpc_stub.get_org_preferences = \
            self.spy(self.sp_rpc_stub.get_org_preferences)
        self.sp_rpc_stub.set_org_preferences = \
            self.spy(self.sp_rpc_stub.set_org_preferences)
        self.sp_rpc_stub.set_quota = Mock()
        self.sp_rpc_stub.remove_quota = Mock()

    def test_set(self):
        from web.views.org_settings.org_settings_view import org_settings

        org_name = u'test'
        request = self._create_request({
            "organization_name": org_name
        })
        request.method = 'POST'

        org_settings(request)

        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()
        self.sp_rpc_stub.set_org_preferences.assert_called_once_with(org_name, None)

    def test_get(self):
        from web.views.org_settings.org_settings_view import org_settings

        org_settings(self._create_request())

        self.assertEqual(self.sp_rpc_stub.set_org_preferences.call_count, 0)
        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()

    def test_remove_quota(self):
        from web.views.org_settings.org_settings_view import org_settings
        org_settings(self._create_quota_setting_request(False, '123'))
        self.sp_rpc_stub.remove_quota.assert_called_once_with()
        self.assertFalse(self.sp_rpc_stub.set_quota.called)

    def test_set_quota_should_handle_whitespace(self):
        from web.views.org_settings.org_settings_view import org_settings
        org_settings(self._create_quota_setting_request(True, '  123\n'))
        self.sp_rpc_stub.set_quota.assert_called_once_with(123)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_handle_zero_quota(self):
        from web.views.org_settings.org_settings_view import org_settings
        org_settings(self._create_quota_setting_request(True, '0'))
        self.sp_rpc_stub.set_quota.assert_called_once_with(0)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_not_proceed_with_negative(self):
        from web.views.org_settings.org_settings_view import org_settings
        org_settings(self._create_quota_setting_request(True, '-123'))
        self.assertFalse(self.sp_rpc_stub.set_quota.called)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_not_proceed_with_invalid_input(self):
        from web.views.org_settings.org_settings_view import org_settings
        org_settings(self._create_quota_setting_request(True, '12.34'))
        self.assertFalse(self.sp_rpc_stub.set_quota.called)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def _create_quota_setting_request(self, enable_quota, quota):
        request = self._create_request({
            "organization_name": 'hoho',
            'quota': quota
        })
        if enable_quota:
            request.params['enable_quota'] = True
        request.method = 'POST'

        return request

    def _create_request(self, params=None):
        request = self.create_dummy_request({} if params is None else params)
        request.registry.settings['show_quota_options'] = True
        return request

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

import unittest
from aerofs_sp.gen.sp_pb2 import GetQuotaReply
from mock import Mock
from ..test_base import TestBase


_GB = 1024 ** 3

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
        from web.views.org_settings.org_settings_view import org_settings_post

        request = self._create_request({
            'quota': 0,
            'tfa-setting': '1',
        })
        request.method = 'POST'

        org_settings_post(request)

        self.assertEqual(self.sp_rpc_stub.set_org_preferences.call_count, 0)

    def test_get(self):
        from web.views.org_settings.org_settings_view import org_settings

        org_settings(self._create_request())

        self.assertEqual(self.sp_rpc_stub.set_org_preferences.call_count, 0)
        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()

    def test_get_quota(self):
        reply = GetQuotaReply()
        # a little less than 9 GB
        reply.quota = 10000000000

        self.sp_rpc_stub.get_quota = Mock(return_value=reply)

        from web.views.org_settings.org_settings_view import org_settings

        ret = org_settings(self._create_request())

        # The returned quota should be rounded down to 9 GB
        self.assertEqual(ret['quota'], 9)

    def test_remove_quota(self):
        from web.views.org_settings.org_settings_view import org_settings_post
        org_settings_post(self._create_quota_setting_request(False, '123'))
        self.sp_rpc_stub.remove_quota.assert_called_once_with()
        self.assertFalse(self.sp_rpc_stub.set_quota.called)

    def test_set_quota_should_handle_whitespace(self):
        from web.views.org_settings.org_settings_view import org_settings_post
        org_settings_post(self._create_quota_setting_request(True, '  123\n'))
        self.sp_rpc_stub.set_quota.assert_called_once_with(123 * _GB)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_handle_zero_quota(self):
        from web.views.org_settings.org_settings_view import org_settings_post
        org_settings_post(self._create_quota_setting_request(True, '0'))
        self.sp_rpc_stub.set_quota.assert_called_once_with(0)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_not_proceed_with_negative(self):
        from web.views.org_settings.org_settings_view import org_settings_post
        org_settings_post(self._create_quota_setting_request(True, '-123'))
        self.assertFalse(self.sp_rpc_stub.set_quota.called)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def test_set_quota_should_not_proceed_with_invalid_input(self):
        from web.views.org_settings.org_settings_view import org_settings_post
        org_settings_post(self._create_quota_setting_request(True, '12.34'))
        self.assertFalse(self.sp_rpc_stub.set_quota.called)
        self.assertFalse(self.sp_rpc_stub.remove_quota.called)

    def _create_quota_setting_request(self, enable_quota, quota):
        request = self._create_request({
            "organization_name": 'hoho',
            'quota': quota,
            'tfa-setting': '1',
        })
        if enable_quota:
            request.params['enable_quota'] = True
        request.method = 'POST'

        return request

    def _create_request(self, params=None):
        request = self.create_dummy_request({} if params is None else params)
        request.registry.settings['show_quota_options'] = True
        request.registry.settings['analytics.enabled'] = False
        return request

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

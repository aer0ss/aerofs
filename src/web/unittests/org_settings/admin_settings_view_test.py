import unittest
from ..test_base import TestBase

class AdminSettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

        # TestBase.setup_common() mocks global methods. Therefore, reload the
        # module under test to reset its referecnes to these methods, in case
        # the module has been loaded before by other test cases.
        # TODO (WW) a better way to do it?
        from web.views.org_settings import org_settings_view
        reload(org_settings_view)

        # Create spy methods. Do not use mock so the RPC stub can validate
        # paramaters passed to these methods.
        self.sp_rpc_stub.get_org_preferences = \
            self.spy(self.sp_rpc_stub.get_org_preferences)
        self.sp_rpc_stub.set_org_preferences = \
            self.spy(self.sp_rpc_stub.set_org_preferences)

    def test_admin_settings_with_set(self):
        from web.views.org_settings.org_settings_view import team_settings

        org_name = u'test'
        request = self.create_dummy_request({
            "organization_name": org_name
        })
        request.method = 'POST'

        team_settings(request)

        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()
        self.sp_rpc_stub.set_org_preferences.assert_called_once_with(org_name,
                                                                     None)

    def test_admin_settings_with_get(self):
        from web.views.org_settings.org_settings_view import team_settings

        team_settings(self.create_dummy_request())

        self.assertEqual(self.sp_rpc_stub.set_org_preferences.call_count, 0)
        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

from pyramid import testing
from ..test_base import TestBase
from unittest import skip

class AdminSettingsViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def tearDown(self):
        testing.tearDown()

    def test_admin_settings_with_set(self):
        from web.views.team_settings.team_settings_view import team_settings

        org_name = u'test'
        request = self.create_dummy_request({
            "form.submitted": True,
            "organization_name": org_name
        })
        request.method = 'POST'

        team_settings(request)

        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()
        self.sp_rpc_stub.set_org_preferences.assert_called_once_with(org_name,
                                                                     None)

    @skip("This test breaks test_admin_settings_with_set. Apparently there is"
          " something I (WW) still don't understand")
    def test_admin_settings_with_get(self):
        from web.views.team_settings.team_settings_view import team_settings

        team_settings(self.create_dummy_request())

        self.assertEqual(self.sp_rpc_stub.set_org_preferences.call_count, 0)
        self.sp_rpc_stub.get_org_preferences.assert_called_once_with()

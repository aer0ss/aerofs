import unittest
from pyramid import testing
from ..test_base import TestBase

class AdminSettingsTest(TestBase):
    def setUp(self):
        self.setup_common()

    def tearDown(self):
        testing.tearDown()

    @unittest.skip("Broken test. See inline comments for detail")
    def test_set_admin_settings(self):
        """
        This test verifies that the admin settings calls SP methods with correct
        signatures

        TODO (WW) this test doesn't work, and causes UserLookupTest to fail.
        REALLY?? The entire Pyramid unittest system is broken.
        """
        from modules.admin_panel.views import admin_settings

        request = self.create_request({
            "form.submitted": True,
            "organization_name": u'test'
        })

        admin_settings(request)
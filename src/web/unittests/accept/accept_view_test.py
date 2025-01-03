from ..test_base import TestBase
import unittest

class AcceptViewTest(TestBase):
    def setUp(self):
        self.setup_common()

    def test_should_call_sp_with_correct_signature(self):

        from web.views.accept.accept_view import \
            accept_folder_invitation, URL_PARAM_SHARE_ID

        sid = 'deadbeef'
        accept_folder_invitation(self.create_dummy_request({
            URL_PARAM_SHARE_ID: sid
        }))

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

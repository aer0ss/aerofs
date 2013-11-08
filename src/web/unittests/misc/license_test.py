import unittest
from datetime import datetime, timedelta

from web.license import _CONF_KEY_LICENSE_VALID_UNTIL, is_license_present_and_valid, _CONF_KEY_LICENSE_TYPE

_CONF_KEY_IS_PRIVATE_DEPLOYMENT = 'config.loader.is_private_deployment'

class LicenseTest(unittest.TestCase):

    def test__is_license_present_and_valid__should_detect_type(self):
        expiry = self._format(datetime.today() + timedelta(days = 1))
        self.assertFalse(is_license_present_and_valid({
            _CONF_KEY_IS_PRIVATE_DEPLOYMENT: 'true',
            _CONF_KEY_LICENSE_VALID_UNTIL: expiry
        }))
        self.assertFalse(is_license_present_and_valid({
            _CONF_KEY_IS_PRIVATE_DEPLOYMENT: 'true',
            _CONF_KEY_LICENSE_TYPE: "abnormal",
            _CONF_KEY_LICENSE_VALID_UNTIL: expiry
        }))
        self.assertTrue(is_license_present_and_valid({
            _CONF_KEY_IS_PRIVATE_DEPLOYMENT: 'true',
            _CONF_KEY_LICENSE_TYPE: "normal",
            _CONF_KEY_LICENSE_VALID_UNTIL: expiry
        }))

    def test__is_license_present_and_valid__should_detect_expiry(self):
        self.assertTrue(self._is_valid_for_delta(timedelta(weeks = 52)))
        self.assertTrue(self._is_valid_for_delta(timedelta(weeks = 4)))
        self.assertTrue(self._is_valid_for_delta(timedelta(weeks = 1)))
        self.assertTrue(self._is_valid_for_delta(timedelta(days = 1)))

        self.assertFalse(self._is_valid_for_delta(timedelta(weeks = -52)))
        self.assertFalse(self._is_valid_for_delta(timedelta(weeks = -4)))
        self.assertFalse(self._is_valid_for_delta(timedelta(weeks = -1)))
        self.assertFalse(self._is_valid_for_delta(timedelta(days = -1)))

    def _is_valid_for_delta(self, delta):
        return is_license_present_and_valid({
            _CONF_KEY_IS_PRIVATE_DEPLOYMENT: 'true',
            _CONF_KEY_LICENSE_TYPE: "normal",
            _CONF_KEY_LICENSE_VALID_UNTIL: self._format(datetime.today() + delta)
        })

    def _format(self, datetime):
        return datetime.strftime("%Y-%m-%d")

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

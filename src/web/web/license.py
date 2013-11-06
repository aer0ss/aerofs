"""
Utility functions to support licensing. See License.java for its counterpart in Java
"""
import base64
import hashlib
import logging
import datetime
from pyramid.security import remember
import requests
from web.util import is_private_deployment
from web.error import error

log = logging.getLogger(__name__)

_CONF_KEY_LICENSE_TYPE = 'license_type'
_CONF_KEY_LICENSE_VALID_UNTIL = 'license_valid_until'

_SESSION_KEY_LICENSE_SHASUM = 'license_shasum'

# 'https://unified.syncfs.com/config' for testing
_URL_LICENSE_HOST = 'http://localhost:5434'
_URL_SET_LICENSE_FILE = _URL_LICENSE_HOST + "/set_license_file"
_URL_CHECK_LICENSE_SHA1 = _URL_LICENSE_HOST + "/check_license_sha1"

def is_license_present(conf):
    """
    Return whether the license exists. Always return true for public deployment
    @param conf a dict of configuration properties
    """
    return not is_private_deployment(conf) or _CONF_KEY_LICENSE_TYPE in conf

def is_license_present_and_valid(conf):
    """
    Return whether the license exists and has not expired. Always return true
    for public deployment.
    @param conf a dict of configuration properties
    """
    if not is_private_deployment(conf): return True

    if not is_license_present(conf): return False

    # We only accept normal licenses for the time being
    if conf.get(_CONF_KEY_LICENSE_TYPE) != "normal": return False

    valid_until = conf.get(_CONF_KEY_LICENSE_VALID_UNTIL)
    # The license is not valid if the expiry date is absent
    if not valid_until: return False

    now = datetime.datetime.today()
    expiry_date = datetime.datetime.strptime(valid_until, "%Y-%m-%d")
    # N.B. keep the comparison consistent with License.java:isValid()
    return now <= expiry_date

def set_license_file_and_shasum(request, license_data):
    """
    Set the license file by calling the config server. Call error() if the
    request failed.
    @param license_data: the bytes of a license file
    """
    r = requests.post(_URL_SET_LICENSE_FILE, data = {
        'license_file': base64.urlsafe_b64encode(license_data)
    })
    if r.status_code == 200:
        log.info("set license file okay: {}".format(r.text))
    else:
        log.error("set license file failed: {} {}".format(r.status_code, r.text))
        error("The provided license file is invalid.")

    _set_license_shasum(license_data, request)
    remember(request, 'license-admin')

def _set_license_shasum(license_data, request):
    shasum = hashlib.sha1(license_data).hexdigest()
    request.session[_SESSION_KEY_LICENSE_SHASUM] = shasum

def is_license_shasum_valid(request):
    """
    Call the config server to verify the shasum saved in the session matches the
    current license data.
    """
    shasum = request.session.get(_SESSION_KEY_LICENSE_SHASUM)
    if not shasum: return False

    r = requests.get(_URL_CHECK_LICENSE_SHA1, params = {
        'license_sha1': shasum
    })
    if r.status_code == 200:
        return True
    else:
        log.error("check license sum failed: {} {}".format(r.status_code, r.text))
        return False

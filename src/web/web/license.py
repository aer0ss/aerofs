"""
Utility functions to support licensing. See License.java for its counterpart in Java
"""
import base64
import hashlib
import logging
import datetime
import requests
from util import is_private_deployment

log = logging.getLogger(__name__)

_CONF_KEY_LICENSE_TYPE = 'license_type'
_CONF_KEY_LICENSE_VALID_UNTIL = 'license_valid_until'

_URL_PARAM_KEY_LICENSE_SHASUM = 'license_shasum'

_SESSION_KEY_LICENSE_SHASUM = 'license_shasum'

# 'https://unified.syncfs.com/config' for testing
_URL_LICENSE_HOST = 'https://unified.syncfs.com/config' # 'http://localhost:5434'
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

def set_license_file(request, license_data):
    """
    Set the license file by calling the config server, and attach the shasum to
    the session. Call error() if the request failed.

    @param license_data: content of a license file as received in HTTP requests

    TODO (WW) read the file the same way as verify_license_file()
    """

    # Due to the way we use JS to upload this file, the request parameter on
    # the wire is urlencoded utf8 of a unicode string.
    # request.params['license'] is that unicode string.
    # We want raw bytes, not the Unicode string, so we encode to latin1
    license_bytes = license_data.encode('latin1')

    r = requests.post(_URL_SET_LICENSE_FILE, data = {
        'license_file': base64.urlsafe_b64encode(license_bytes)
    })
    if r.status_code == 200:
        log.info("set license file okay: {}".format(r.text))
        shasum = hashlib.sha1(license_bytes).hexdigest()
        _attach_checksum_to_session(request, shasum)
        return True
    else:
        log.error("set license file failed: {} {}".format(r.status_code, r.text))
        return False

def verify_license_file(request, license_file):
    """
    Verify the shasum of the provided file with the config server, and attach
    the checksum to the session if verification is successful.
    @return whether verification succeeds
    """
    digest = hashlib.sha1()
    while True:
        buf = license_file.read(4096)
        if not buf: break
        digest.update(buf)
    shasum = digest.hexdigest()

    if is_license_shasum_valid(shasum):
        _attach_checksum_to_session(request, shasum)
        return True
    else:
        return False

def verify_license_shasum(request, shasum):
    """
    Verify the shasum provided in the request param, and attach the checksum to
    the session if verification is successful.
    @return whether verification succeeds
    """
    if shasum and is_license_shasum_valid(shasum):
        _attach_checksum_to_session(request, shasum)
        return True
    else:
        return False

def _attach_checksum_to_session(request, shasum):
    request.session[_SESSION_KEY_LICENSE_SHASUM] = shasum

def get_license_shasum_from_query(request):
    """
    Return the license shasum specified in the request query string. Return None
    if no shasum is specified.
    """
    return request.params.get(_URL_PARAM_KEY_LICENSE_SHASUM)

def get_license_shasum_from_session(request):
    """
    Return the license shasum attached to the session by one of the verify*()
    methods. Return None if no shasum is attached.

    N.B. The returned shasum is potentially un-verified if the user tempers
    with session cookies.
    """
    return request.session.get(_SESSION_KEY_LICENSE_SHASUM)

def is_license_shasum_valid(shasum):
    """
    Call the config server to verify the shasum saved in the session matches the
    current license data. This method assumes the shasum is non-null.
    """
    r = requests.get(_URL_CHECK_LICENSE_SHA1, params = {
        'license_sha1': shasum
    })
    if r.status_code == 200:
        return True
    else:
        log.error("check license sum failed: {} {}".format(r.status_code, r.text))
        return False

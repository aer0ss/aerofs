"""
Utility functions to support licensing. See License.java for its counterpart in Java
"""
import datetime
import hashlib
import logging

from aerofs_common.configuration import Configuration
import requests

from util import is_private_deployment

log = logging.getLogger(__name__)

_CONF_KEY_LICENSE_TYPE = 'license_type'
_CONF_KEY_LICENSE_VALID_UNTIL = 'license_valid_until'

URL_PARAM_KEY_LICENSE_SHASUM = 'license_shasum'

_SESSION_KEY_LICENSE_SHASUM = 'license_shasum'

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

def set_license_file_and_attach_shasum_to_session(request, license_bytes):
    """
    Set the license file by calling the config server, and attach the shasum to
    the session. Call error() if the request failed.

    @param license_bytes: content of a license file
    """
    conf_client = Configuration(request.registry.settings['deployment.config_server_uri'], service_name='bunker')
    try:
        text = conf_client.set_license(license_bytes)
        log.info("set license file okay: {}".format(text))
        shasum = hashlib.sha1(license_bytes).hexdigest()
        _attach_checksum_to_session(request, shasum)
        return True
    except requests.HTTPError as e:
        log.error("set license file failed: {} {}".format(e.response.status_code, e.response.text))
        return False

def verify_license_shasum_and_attach_to_session(request, shasum):
    """
    Verify the shasum provided in the request param, and attach the checksum to
    the session if verification is successful.
    @return whether verification succeeds
    """
    if shasum and is_license_shasum_valid(request, shasum):
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
    return request.params.get(URL_PARAM_KEY_LICENSE_SHASUM)

def get_license_shasum_from_session(request):
    """
    Return the license shasum attached to the session by one of the verify*()
    methods. Return None if no shasum is attached.

    N.B. The returned shasum is potentially un-verified if the user tempers
    with session cookies.
    """
    return request.session.get(_SESSION_KEY_LICENSE_SHASUM)

def is_license_shasum_valid(request, shasum):
    """
    Call the config server to verify the shasum saved in the session matches the
    current license data. This method assumes the shasum is non-null.
    """
    client = Configuration(request.registry.settings['deployment.config_server_uri'], service_name='bunker')
    try:
        client.is_license_shasum_valid(shasum)
        return True
    except requests.HTTPError as e:
        log.error("check license sum failed: {} {}".format(e.response.status_code, e.response.text))
        return False

#
# This file contains utility functions for AeroFS OAuth clients
#
import logging
import re
import requests
from web import util
from web.util import flash_error, get_rpc_stub

log = logging.getLogger(__name__)


# The URL to Bifrost, i.e. the OAuth server
BIFROST_URL = "http://localhost:8700"


def retrieve_access_token(request):
    """
    Assuming the current session has logged in to SP, this method request an
    access token from Bifrost, and return it to the caller. Standard AeroFS
    exception types are thrown on errors.
    """
    log.info('retrieve_access_token() gets access code')
    sp = get_rpc_stub(request)
    access_code = sp.get_mobile_access_code().accessCode

    log.info('retrieve_access_token() get access token')
    r = requests.post(BIFROST_URL + "/token", data = {
        "client_id": "aerofs-web",
        "client_secret": "ceci-nest-pas-un-secret-de-web",
        "grant_type": "authorization_code",
        "code_type": "device_authorization",
        "code": access_code,
    })
    if not r.ok:
        raise_error_for_bifrost_response(r)

    return r.json()["access_token"]


def is_builtin_client_id(client_id):
    """
    Some client IDs are for internal use and not to exposed to end users, such
    as 'aerofs-ios.' This method returns whether the given client ID is internal.
    """
    return len(client_id) != 36

def is_valid_access_token(token):
    return re.match("^[0-9a-f]{32}$", token)

def is_valid_non_builtin_client_id(client_id):
    return re.match("^[\-0-9a-f]{36}$", client_id)

def flash_error_for_bifrost_response(request, response):
    flash_error(request, _get_error_message_for_bifrost_resonse(response))


def raise_error_for_bifrost_response(response):
    util.error(_get_error_message_for_bifrost_resonse(response))


def _get_error_message_for_bifrost_resonse(response):
    log.error('bifrost error: {} text: {}'.format(response.status_code, response.text))
    return 'An error occurred. Please try again. The error is: {} {}'\
            .format(response.status_code, response.text)

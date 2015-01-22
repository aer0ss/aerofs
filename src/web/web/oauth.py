#
# This file contains utility functions for AeroFS OAuth clients
#
import logging
import re
import requests
from web import util
from web.util import flash_error
from web.util import get_rpc_stub

log = logging.getLogger(__name__)


# The URL to Bifrost, i.e. the OAuth server
def get_bifrost_url(request):
    return request.registry.settings["deployment.oauth_server_uri"]

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

def is_aerofs_mobile_client_id(client_id):
    return client_id in ('aerofs-android', 'aerofs-ios')

def flash_error_for_bifrost_response(request, response):
    flash_error(request, _get_error_message_for_bifrost_resonse(response))


def raise_error_for_bifrost_response(response):
    util.error(_get_error_message_for_bifrost_resonse(response))


def _get_error_message_for_bifrost_resonse(response):
    log.error('bifrost error: {} text: {}'.format(response.status_code, response.text))
    return 'An error occurred. Please try again. The error is: {} {}'\
            .format(response.status_code, response.text)

def delete_all_tokens(request, owner):
    return requests.delete(get_bifrost_url(request) + '/users/' + owner + '/tokens')

def delete_delegated_tokens(request, owner):
    return requests.delete(get_bifrost_url(request) + '/users/' + owner + '/delegates')

def get_new_oauth_token(request, client_id, client_secret, expires_in=0, scopes=None):
    # N.B. (JG) the get_mobile_access_code RPC returns a proof-of-identity nonce that
    # Bifrost uses for authentication. It was originally designed for a mobile app
    # and its original name remains to maintain backwards compatibility.

    data={
        'grant_type': 'authorization_code',
        'code': get_rpc_stub(request).get_mobile_access_code().accessCode,
        'code_type': 'device_authorization',
        'client_id': client_id,
        'client_secret': client_secret,
        'expires_in': expires_in,
    }

    if scopes is not None:
        data['scope'] = ','.join(scopes)
    r = requests.post(request.registry.settings["deployment.oauth_server_uri"]+'/token', data)
    r.raise_for_status()
    token = r.json()['access_token']
    return token

def delete_oauth_token(request, token):
    r = requests.delete(request.registry.settings["deployment.oauth_server_uri"]+'/token/'+token)
    if r.status_code == 404:
        return
    r.raise_for_status()

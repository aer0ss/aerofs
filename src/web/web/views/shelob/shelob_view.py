import logging

from pyramid.view import view_config
from pyramid.response import Response
from pyramid.security import authenticated_userid
import requests

from web.util import get_rpc_stub


log = logging.getLogger(__name__)

_SHELOB_CLIENT_ID = 'aerofs-shelob'
_SHELOB_CLIENT_SECRET = None


@view_config(
        route_name='files',
        renderer='shelob.mako',
        permission='user',
        request_method='GET',
)
def files(request):
    return {}


def get_shelob_secret(request):
    """
    Get Shelob's OAuth client secret from local cache or from Bifrost, or
    create the client on Bifrost if necessary.
    """
    # Get secret from in-memory cache if available
    global _SHELOB_CLIENT_SECRET
    if _SHELOB_CLIENT_SECRET is not None:
        return _SHELOB_CLIENT_SECRET
    # Get secret from Bifrost if not available locally
    BIFROST_CLIENTS_ENDPOINT = request.registry.settings["deployment.oauth_server_uri"] + '/clients/'
    r = requests.get(BIFROST_CLIENTS_ENDPOINT + _SHELOB_CLIENT_ID)
    if r.ok:
        _SHELOB_CLIENT_SECRET = r.json()['secret']
    elif r.status_code == 404:
        # If Bifrost says that the client does not exist, create it. The secret
        # is included in the response.
        r = requests.post(BIFROST_CLIENTS_ENDPOINT,
            data={
                'client_id': _SHELOB_CLIENT_ID,
                'client_name': 'AeroFS Web Access',
                'redirect_uri': 'aerofs://redirect',
                'resource_server_key': 'oauth-havre',
                'expires': 900,  # expiry time of 900 seconds (15 minutes)
            }
        )
        r.raise_for_status()
        _SHELOB_CLIENT_SECRET = r.json()['secret']
    else:
        r.raise_for_status()
    return _SHELOB_CLIENT_SECRET


# map of userid -> access_token
_oauth_token = {}

def _get_new_oauth_token(request):
    # N.B. (JG) the get_mobile_access_code RPC returns a proof-of-identity nonce that
    # Bifrost uses for authentication. It was originally designed for a mobile app
    # and its original name remains to maintain backwards compatibility
    r = requests.post(request.registry.settings["deployment.oauth_server_uri"]+'/token',
        data={
            'grant_type': 'authorization_code',
            'code': get_rpc_stub(request).get_mobile_access_code().accessCode,
            'code_type': 'device_authorization',
            'client_id': _SHELOB_CLIENT_ID,
            'client_secret': get_shelob_secret(request),
        }
    )
    r.raise_for_status()
    token = r.json()['access_token']
    return token


@view_config(
    route_name='json_token',
    permission='user',
    renderer='json',
    request_method='GET',
)
def json_token(request):
    """
    Return an OAuth token for the requester. If there isn't a token cached for the user,
    then fetch a new token from Bifrost.
    """
    userid = authenticated_userid(request)
    if userid not in _oauth_token:
        _oauth_token[userid] = _get_new_oauth_token(request)
    return {'token': _oauth_token[userid]}


@view_config(
    route_name='json_new_token',
    permission='user',
    renderer='json',
    request_method='GET',
)
def json_new_token(request):
    """
    Fetch a new token for the user, even if one is cached already.
    """
    userid = authenticated_userid(request)
    _oauth_token[userid] = _get_new_oauth_token(request)
    return {'token': _oauth_token[userid]}


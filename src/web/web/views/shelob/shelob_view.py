import logging

from pyramid.view import view_config
from pyramid.response import Response
from pyramid.security import authenticated_userid
import requests

from web.util import get_rpc_stub


log = logging.getLogger(__name__)


@view_config(
        route_name='files',
        renderer='shelob.mako',
        permission='user',
        request_method='GET',
)
def files(request):
    return {}


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
            'client_id': 'aerofs-shelob',
            'client_secret': request.registry.settings["oauth.shelob_client_secret"],
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


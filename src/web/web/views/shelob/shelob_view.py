import logging

from pyramid.view import view_config
from pyramid.security import authenticated_userid

from web.util import get_rpc_stub
from web.oauth import get_bifrost_client

log = logging.getLogger(__name__)

def get_new_shelob_token(request):
    client_id = 'aerofs-shelob'
    client_secret = request.registry.settings["oauth.shelob_client_secret"]
    sp_client = get_rpc_stub(request)
    access_code = sp_client.get_mobile_access_code().accessCode
    bifrost_client = get_bifrost_client(request)
    return bifrost_client.get_new_oauth_token(access_code,
            client_id, client_secret, expires_in=0, scopes=[
                'files.read', 'files.write', 'acl.read'
            ]
    )


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


@view_config(
    route_name='json_token',
    permission='user',
    http_cache = 0,
    renderer='json',
    request_method='POST',
)
def json_token(request):
    """
    Return an OAuth token for the requester. If there isn't a token cached for the user,
    then fetch a new token from Bifrost.
    """
    userid = authenticated_userid(request)
    if userid not in _oauth_token:
        _oauth_token[userid] = get_new_shelob_token(request)
    return {'token': _oauth_token[userid]}


@view_config(
    route_name='json_new_token',
    permission='user',
    http_cache = 0,
    renderer='json',
    request_method='POST',
)
def json_new_token(request):
    """
    Fetch a new token for the user, even if one is cached already.
    """
    userid = authenticated_userid(request)
    _oauth_token[userid] = get_new_shelob_token(request)
    return {'token': _oauth_token[userid]}


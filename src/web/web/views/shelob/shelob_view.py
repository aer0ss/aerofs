import logging

from pyramid.view import view_config
from pyramid.response import Response
from pyramid.security import authenticated_userid
import requests

from oauth import get_new_oauth_token


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
        _oauth_token[userid] = get_new_oauth_token(request)
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
    _oauth_token[userid] = get_new_oauth_token(request)
    return {'token': _oauth_token[userid]}


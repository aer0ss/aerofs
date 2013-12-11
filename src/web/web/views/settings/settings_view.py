import logging
from pyramid.security import authenticated_userid
import requests

from pyramid.view import view_config
from web import util
from web.views.oauth import flash_error_for_bifrost_response, BIFROST_URL, \
    is_builtin_client_id, raise_error_for_bifrost_response, is_valid_access_token


log = logging.getLogger("web")


@view_config(
    route_name='access_tokens',
    permission='user',
    renderer='access_tokens.mako',
    request_method='GET'
)
def access_tokens(request):
    r = requests.get(BIFROST_URL + '/tokenlist', params={
        'owner': authenticated_userid(request)
    })
    if r.ok:
        # clients is an array of registered clients
        tokens = r.json()['tokens']
        # skip built-in apps
        tokens = [token for token in tokens if not is_builtin_client_id(token['client_id'])]
    else:
        tokens = []
        flash_error_for_bifrost_response(request, r)

    return {
        'tokens': tokens
    }


@view_config(
    route_name='json_delete_access_token',
    permission='user',
    request_method='POST',
    renderer='json'
)
def json_delete_access_token(request):
    token = request.params['access_token']
    # This is to prevent injection attacks e.g. token='../clients'
    if not is_valid_access_token(token):
        log.error('json_delete_access_token(): invalid token: ' + token)
        util.error('The application ID is invalid.')

    r = requests.delete(BIFROST_URL + '/token/{}'.format(token))
    if not r.ok:
        raise_error_for_bifrost_response(r)
    return {}
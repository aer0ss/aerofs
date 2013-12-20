import logging
from pyramid.httpexceptions import HTTPFound, HTTPBadRequest, HTTPInternalServerError
from pyramid.security import authenticated_userid
import requests

from pyramid.view import view_config
from web import util
from web.oauth import flash_error_for_bifrost_response, BIFROST_URL, \
    is_builtin_client_id, raise_error_for_bifrost_response, is_valid_access_token

log = logging.getLogger(__name__)


class RedirectWithParams(HTTPFound):
    def __init__(self, location, params, state=None):
        if state is not None:
            params.update({"state": state})
        sep = '&' if '?' in location else '?'
        paramstring = '&'.join("{}={}".format(k, v.encode("utf8")) for k, v in params.iteritems())
        super(RedirectWithParams, self).__init__(
            location=location + sep + paramstring,
            body_template=''
        )

def get_exactly_one_or_throw_400(params, param):
    try:
        val = params.getone(param)
    except KeyError:
        raise HTTPBadRequest(detail="param must be specified exactly once: {}".format(param))
    return val


@view_config(
    route_name='app_authorization',
    permission='user',
    renderer='app_authorization.mako',
    request_method='GET'
)
def show_authorization_page(request):
    client_id = get_exactly_one_or_throw_400(request.params, "client_id")
    user_redirect_uri = get_exactly_one_or_throw_400(request.params, "redirect_uri")

    r = requests.get(BIFROST_URL + "/clients/" + client_id)
    if r.status_code == 404:
        raise HTTPBadRequest(detail="client_id {} not found".format(client_id))
    if not r.ok:
        raise HTTPInternalServerError()
    client_name = r.json()["client_name"]
    redirect_uri = r.json()["redirect_uri"]

    if redirect_uri != user_redirect_uri:
        s = "The redirect_uri specified does not match the one specified during client registration"
        raise HTTPBadRequest(detail=s)

    # if we reach here, the redirect_uri is valid, and all errors can be sent
    # to the redirect_uri

    user_state = request.params.get("state")

    response_type = request.params.getall("response_type")
    if len(response_type) != 1:
        raise RedirectWithParams(location=redirect_uri, state=user_state, params={
            "error": "invalid_request",
            "error_description": "\"response_type\" query param must be specified exactly once"
        })
    response_type = response_type[0]

    if response_type != "code":
        raise RedirectWithParams(location=redirect_uri, state=user_state, params={
            "error": "unsupported_response_type",
            "error_description": "\"response_type\" param must be \"code\""
        })

    # get identity nonce so user can request an access code
    sp = util.get_rpc_stub(request)
    nonce = sp.get_mobile_access_code().accessCode

    return {
        'client_name': client_name,
        'response_type': response_type,
        'client_id': client_id,
        'identity_nonce': nonce,
        'redirect_uri': redirect_uri,
        'state': user_state,
    }


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

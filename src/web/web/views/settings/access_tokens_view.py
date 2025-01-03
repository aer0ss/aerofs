import binascii
import json
import logging

from pyramid.httpexceptions import HTTPFound, HTTPBadRequest, HTTPInternalServerError
from pyramid.security import authenticated_userid
from pyramid.view import view_config

from web import util
from web.oauth import get_bifrost_client, get_privileged_bifrost_client, \
        is_valid_access_token, is_builtin_client_id

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
def app_authorization(request):
    client_id = get_exactly_one_or_throw_400(request.params, "client_id")
    user_redirect_uri = get_exactly_one_or_throw_400(request.params, "redirect_uri")

    # verify that the client id is for a valid app
    bifrost_client = get_privileged_bifrost_client(request, service_name="web")
    r = bifrost_client.get_app_info(client_id)
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

    scope = request.params.getall("scope")
    if len(scope) != 1:
        raise RedirectWithParams(location=redirect_uri, state=user_state, params={
            "error": "invalid_request",
            "error_description": "\"scope\" query param must be specified exactly once"
        })
    raw_scopes = scope[0].split(",")

    # get identity nonce so user can request an access code
    sp = util.get_rpc_stub(request)
    nonce = sp.get_access_code().accessCode

    # get shared folder names, for fined-grained scopes
    shares = {}
    for store in sp.get_acl(0).store_acl:
        shares[binascii.hexlify(store.store_id).lower()] = store.name

    log.info(shares)

    # filter invalid scopes, reorganize in more display-friendly fashion
    valid_scopes = {
        "files.read": True,
        "files.write": True,
        "files.appdata": False,
        "user.read": False,
        "user.write": False,
        "user.password": False,
        "acl.read": True,
        "acl.write": True,
        "acl.invitations": False,
        "organization.admin": False,
        "groups.read": False,
    }

    scopes = {}
    for s in raw_scopes:
        ss = s.split(":")
        if len(ss) == 1:
            if s in valid_scopes:
                scopes[s] = []
        elif len(ss) == 2:
            scope_name = ss[0]
            scope_qual = ss[1].lower()
            if scope_name in valid_scopes and valid_scopes[scope_name] and scope_qual in shares:
                if scope_name not in scopes:
                    scopes[scope_name] = [scope_qual]
                elif len(scopes[scope_name]) > 0 and scope_qual not in scopes[scope_name]:
                    scopes[scope_name].append(scope_qual)

    if len(scopes) == 0:
        raise RedirectWithParams(location=redirect_uri, state=user_state, params={
            "error": "invalid_request",
            "error_description": "\"scope\" query param must contain at least one valid scope."
        })

    log.info(scopes)

    return {
        'client_name': client_name,
        'response_type': response_type,
        'client_id': client_id,
        'identity_nonce': nonce,
        'redirect_uri': redirect_uri,
        'state': user_state,
        'scopes': json.dumps(scopes),
        'shares': json.dumps(shares)
    }


@view_config(
    route_name='access_tokens',
    permission='user',
    renderer='access_tokens.mako',
    request_method='GET'
)
def access_tokens(request):
    bifrost_client = get_privileged_bifrost_client(request, service_name="web")
    r = bifrost_client.get_access_tokens_for(authenticated_userid(request))
    if r.ok:
        # Skip builtin clients (e.g. android, ios, web acess tokens)
        tokens = [t for t in r.json()['tokens'] if not is_builtin_client_id(t['client_id'])]
    else:
        tokens = []
        bifrost_client.flash_on_error(request, r)

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
    try:
        token = request.params['access_token']
    except KeyError:
        token = request.json_body['access_token']
    # This is to prevent injection attacks e.g. token='../clients'
    if not is_valid_access_token(token):
        log.error('json_delete_access_token(): invalid token: ' + token)
        util.expected_error('The application ID is invalid.')

    bifrost_client = get_bifrost_client(request)
    bifrost_client.delete_access_token(token)
    bifrost_client.raise_on_error()
    return {}

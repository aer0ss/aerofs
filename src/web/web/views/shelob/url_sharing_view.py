import logging
import time

from aerofs_sp.gen.common_pb2 import PBException
from pyramid.view import view_config

from oauth import get_new_oauth_token, delete_oauth_token
from web import error
from web.sp_util import exception2error
from web.util import get_rpc_stub


log = logging.getLogger(__name__)


def _get_new_zelda_token(request, expires_in):
    client_id = 'aerofs-zelda'
    client_secret = request.registry.settings["oauth.zelda_client_secret"]
    return get_new_oauth_token(request, client_id, client_secret, expires_in)


def _abs_milli_to_delta_seconds(abs_milli):
    if abs_milli == 0:
        return 0
    return int(abs_milli / 1000 - time.time())


def _delta_seconds_to_abs_milli(delta_seconds):
    if delta_seconds == 0:
        return 0
    return int((delta_seconds + time.time()) * 1000)


def _pb_rest_object_url_to_dict(pb):
    return {
        'key': pb.key,
        'token': pb.token,
        'soid': pb.soid,
        'has_password': getattr(pb, 'has_password', False),
        'expires': _abs_milli_to_delta_seconds(getattr(pb, 'expires', 0)),
    }


@view_config(
        route_name='create_url',
        renderer='json',
        permission='user',
        request_method='POST',
)
def create_url(request):
    soid = request.POST.get("soid")
    if soid is None:
        error.error('missing "soid" param')
    token = _get_new_zelda_token(request, 0)  # N.B. 0 means no expiry
    reply = exception2error(get_rpc_stub(request).create_url, (soid, token), {
        PBException.BAD_ARGS: "The SOID provided was invalid",
        PBException.NO_PERM: "You are not an owner of the shared folder",
    })
    key = reply.url_info.key

    return {"key": key}


@view_config(
        route_name='get_url',
        renderer='json',
        request_method='GET',
)
def get_url(request):
    key = request.matchdict['key']
    reply = exception2error(get_rpc_stub(request).get_url_info, (key, None), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
    })
    # TODO: deal with passwords
    return _pb_rest_object_url_to_dict(reply.url_info)


@view_config(
        route_name='set_url_expires',
        renderer='json',
        permission='user',
        request_method='POST',
)
def set_url_expires(request):
    """
    expires: url duration in seconds (number of seconds until expiry)
    """
    key = request.POST.get("key")
    expires = request.POST.get("expires")
    if key is None:
        error.error('missing "key" param')
    if expires is None:
        error.error('missing "expires" param')
    try:
        expires_abs_milli = _delta_seconds_to_abs_milli(int(expires))
    except ValueError:
        error.error('"expires" param must be a valid number')
    new_token = _get_new_zelda_token(request, int(expires))
    old_token = exception2error(get_rpc_stub(request).get_url_info, (key, None), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
    }).url_info.token
    delete_oauth_token(request, old_token)
    exception2error(get_rpc_stub(request).set_url_expires, (key, expires_abs_milli, new_token), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
        PBException.NO_PERM: "The user is not an owner of the shared folder",
    })

    return {}


@view_config(
        route_name='remove_url_expires',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url_expires(request):
    key = request.POST.get("key")
    if key is None:
        error.error('missing "key" param')
    new_token = _get_new_zelda_token(request, 0)
    old_token = exception2error(get_rpc_stub(request).get_url_info, (key, None), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
    }).url_info.token
    delete_oauth_token(request, old_token)
    exception2error(get_rpc_stub(request).remove_url_expires, (key, new_token), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
        PBException.NO_PERM: "The user is not an owner of the shared folder",
    })

    return {}


@view_config(
        route_name='remove_url',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url(request):
    key = request.POST.get("key")
    if key is None:
        error.error('missing "key" param')
    old_token = exception2error(get_rpc_stub(request).get_url_info, (key, None), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
    }).url_info.token
    delete_oauth_token(request, old_token)
    exception2error(get_rpc_stub(request).remove_url, (key,), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
        PBException.NO_PERM: "The user is not an owner of the shared folder",
    })

    return {}


@view_config(
        route_name='set_url_password',
        renderer='json',
        permission='user',
        request_method='POST',
)
def set_url_password(request):
    key = request.POST.get("key")
    password = request.POST.get("password")
    if key is None:
        error.error('missing "key" param')
    if password is None:
        error.error('missing "password" param')
    password = password.encode('utf-8')
    reply = exception2error(get_rpc_stub(request).get_url_info, (key, None), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
    })
    new_token = _get_new_zelda_token(request, _abs_milli_to_delta_seconds(reply.url_info.expires))
    delete_oauth_token(request, reply.url_info.token)
    reply = exception2error(get_rpc_stub(request).set_url_password, (key, password, new_token), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
        PBException.NO_PERM: "The user is not an owner of the shared folder",
    })

    return {}


@view_config(
        route_name='remove_url_password',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url_password(request):
    key = request.POST.get("key")
    if key is None:
        error.error('missing "key" param')
    exception2error(get_rpc_stub(request).remove_url_password, (key,), {
        PBException.NOT_FOUND: "The link does not exist or has expired",
        PBException.NO_PERM: "The user is not an owner of the shared folder",
    })

    return {}


@view_config(
        route_name='list_urls_for_store',
        renderer='json',
        permission='user',
        request_method='GET',
)
def list_urls_for_store(request):
    sid = request.params.get("sid")
    if sid is None:
        error.error('missing "sid" param')
    sid_bytes = 'root'.encode('utf-8') if sid == 'root' else sid.decode('hex')
    reply = exception2error(get_rpc_stub(request).list_urls_for_store, (sid_bytes,), {
        PBException.NOT_FOUND: "The shared folder does not exist",
        PBException.NO_PERM: "The user is not amanager of the shared folder",
    })

    return {'urls': [_pb_rest_object_url_to_dict(u) for u in reply.url]}


import logging
import time
import json
from datetime import datetime

from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.httpexceptions import HTTPUnauthorized, HTTPForbidden, HTTPNotFound
from pyramid.view import view_config
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid
import requests

from oauth import get_new_oauth_token, delete_oauth_token
from web import error
from web.sp_util import exception2error
from web.util import get_rpc_stub, is_private_deployment, str2bool


log = logging.getLogger(__name__)


def _audit(request, topic, event, data=None):
    if not is_private_deployment(request.registry.settings) or \
            not str2bool(request.registry.settings["base.audit.enabled"]):
        return
    data = data or {}
    assert type(data) == dict
    data['timestamp'] = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S.%f%z")
    data['topic'] = topic
    data['event'] = event
    data['ip'] = request.client_addr
    host = request.registry.settings["base.audit.service.host"]
    port = request.registry.settings["base.audit.service.port"]
    path = request.registry.settings["base.audit.service.path"]
    headers = {'Content-type': 'application/json'}
    requests.post("http://{}:{}{}".format(host, port, path), data=json.dumps(data), headers=headers)


def _get_new_zelda_token(request, expires_in, soid):
    client_id = 'aerofs-zelda'
    client_secret = request.registry.settings["oauth.zelda_client_secret"]
    scopes = ['files.read:' + soid, "linksharing"]
    return get_new_oauth_token(request, client_id, client_secret, expires_in, scopes)


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

def _make_sp_request(fn, args):
    try:
        return exception2error(fn, args, {
            PBException.BAD_ARGS: "400 Bad Request",
            PBException.NO_PERM: "403 Forbidden",
        })
    except ExceptionReply as e:
        if e.get_type() == PBException.BAD_CREDENTIAL:
            raise HTTPUnauthorized()
        if e.get_type() == PBException.NO_PERM:
            raise HTTPForbidden()
        if e.get_type() == PBException.NOT_FOUND:
            raise HTTPNotFound()
        raise e


@view_config(
        route_name='create_url',
        renderer='json',
        permission='user',
        request_method='POST',
)
def create_url(request):
    soid = request.json_body.get("soid")
    if soid is None:
        error.error('missing "soid" param')
    token = _get_new_zelda_token(request, 0, soid)  # N.B. 0 means no expiry
    reply = _make_sp_request(get_rpc_stub(request).create_url, (soid, token))
    key = reply.url_info.key

    assert len(reply.url_info.soid) == 64, reply.url_info.soid

    _audit(request, "LINK", "link.create", {
        'caller': authenticated_userid(request),
        'key': key,
        'soid': {'sid': reply.url_info.soid[:32], 'oid': reply.url_info.soid[32:]},
    })

    return {"key": key}


@view_config(
        route_name='get_url',
        renderer='linkshare.mako',
        permission=NO_PERMISSION_REQUIRED,
)
def get_url(request):
    key = request.matchdict['key']
    return {'key': key}


@view_config(
        route_name='get_url_info',
        renderer='json',
        permission=NO_PERMISSION_REQUIRED,
)
def get_url_info(request):
    key = request.matchdict['key']
    password = request.json_body.get('password')
    if password is not None:
        password = password.encode('utf-8')
    reply = _make_sp_request(get_rpc_stub(request).get_url_info, (key, password))
    _audit(request, "LINK", "link.access", {'key': key})
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
    key = request.json_body.get("key")
    expires = request.json_body.get("expires")
    if key is None:
        error.error('missing "key" param')
    if expires is None:
        error.error('missing "expires" param')
    try:
        expires_abs_milli = _delta_seconds_to_abs_milli(int(expires))
    except ValueError:
        error.error('"expires" param must be a valid number')

    url_info = _make_sp_request(get_rpc_stub(request).get_url_info, (key, None)).url_info
    old_token = url_info.token
    soid = url_info.soid
    new_token = _get_new_zelda_token(request, int(expires), soid)
    delete_oauth_token(request, old_token)
    _make_sp_request(get_rpc_stub(request).set_url_expires, (key, expires_abs_milli, new_token))

    _audit(request, "LINK", "link.set_expiry", {
        'caller': authenticated_userid(request),
        'key': key,
        'expiry': int(expires),
    })

    return {}


@view_config(
        route_name='remove_url_expires',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url_expires(request):
    key = request.json_body.get("key")
    if key is None:
        error.error('missing "key" param')
    url_info = _make_sp_request(get_rpc_stub(request).get_url_info, (key, None)).url_info
    old_token = url_info.token
    soid = url_info.soid
    new_token = _get_new_zelda_token(request, 0, soid)
    delete_oauth_token(request, old_token)
    _make_sp_request(get_rpc_stub(request).remove_url_expires, (key, new_token))

    _audit(request, "LINK", "link.remove_expiry", {
        'caller': authenticated_userid(request),
        'key': key,
    })

    return {}


@view_config(
        route_name='remove_url',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url(request):
    key = request.json_body.get("key")
    if key is None:
        error.error('missing "key" param')
    old_token = _make_sp_request(get_rpc_stub(request).get_url_info, (key, None)).url_info.token
    delete_oauth_token(request, old_token)
    _make_sp_request(get_rpc_stub(request).remove_url, (key,))

    _audit(request, "LINK", "link.delete", {
        'caller': authenticated_userid(request),
        'key': key,
    })

    return {}


@view_config(
        route_name='set_url_password',
        renderer='json',
        permission='user',
        request_method='POST',
)
def set_url_password(request):
    key = request.json_body.get("key")
    password = request.json_body.get("password")
    if key is None:
        error.error('missing "key" param')
    if password is None:
        error.error('missing "password" param')
    password = password.encode('utf-8')
    reply = _make_sp_request(get_rpc_stub(request).get_url_info, (key, None))
    old_expires = _abs_milli_to_delta_seconds(reply.url_info.expires)
    new_token = _get_new_zelda_token(request, old_expires, reply.url_info.soid)
    delete_oauth_token(request, reply.url_info.token)
    reply = _make_sp_request(get_rpc_stub(request).set_url_password, (key, password, new_token))

    _audit(request, "LINK", "link.set_password", {
        'caller': authenticated_userid(request),
        'key': key,
    })

    return {}


@view_config(
        route_name='remove_url_password',
        renderer='json',
        permission='user',
        request_method='POST',
)
def remove_url_password(request):
    key = request.json_body.get("key")
    if key is None:
        error.error('missing "key" param')
    _make_sp_request(get_rpc_stub(request).remove_url_password, (key,))

    _audit(request, "LINK", "link.remove_password", {
        'caller': authenticated_userid(request),
        'key': key,
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
    reply = _make_sp_request(get_rpc_stub(request).list_urls_for_store, (sid_bytes,))

    return {'urls': [_pb_rest_object_url_to_dict(u) for u in reply.url]}


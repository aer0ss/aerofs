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

from web import error
from web.analytics import send_analytics_event
from web.sp_util import exception2error
from web.util import get_deployment_secret, get_rpc_stub, str2bool


log = logging.getLogger(__name__)


def _audit(request, topic, event, data=None):
    if not str2bool(request.registry.settings["base.audit.enabled"]):
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
    secret = get_deployment_secret(request.registry.settings)
    auth_header_value = 'Aero-Service-Shared-Secret {} {}'.format('web', secret)
    headers = {'Content-type': 'application/json',
               'Authorization': auth_header_value}
    requests.post("http://{}:{}{}".format(host, port, path), data=json.dumps(data), headers=headers)


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
        'require_login': getattr(pb, 'require_login', False),
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
        if e.get_type() == PBException.NOT_AUTHENTICATED:
            raise HTTPUnauthorized(detail='Log in required.')
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
        error.expected_error('missing "soid" param')
    reply = _make_sp_request(get_rpc_stub(request).create_url, (soid))
    key = reply.url_info.key

    return {
        "key": key,
        "require_login": reply.url_info.require_login,
        "has_password": reply.url_info.has_password,
        "expires": reply.url_info.expires
    }


@view_config(
        route_name='get_url',
        renderer='linkshare.mako',
        permission=NO_PERMISSION_REQUIRED,
)
def get_url(request):
    send_analytics_event(request, "ACTIVE_USER")
    key = request.matchdict['key']
    return {'key': key}


@view_config(
        route_name='audit_link_download',
        renderer='json',
        http_cache = 0,
        permission=NO_PERMISSION_REQUIRED,
)
def audit_link_download(request):
    key = request.json_body.get("key")
    oid = request.json_body.get("oid")
    name = request.json_body.get("name")
    _audit(request, "LINK", "link.download", {'key': key, 'oid': oid, 'name': name})
    return {}


@view_config(
        route_name='get_url_info',
        renderer='json',
        http_cache = 0,
        permission=NO_PERMISSION_REQUIRED,
)
def get_url_info(request):
    key = request.matchdict['key']
    password = request.json_body.get('password')
    if password is not None:
        password = password.encode('utf-8')
    reply = _make_sp_request(get_rpc_stub(request).get_url_info, (key, password))
    return _pb_rest_object_url_to_dict(reply.url_info)


@view_config(
        route_name='set_url_require_login',
        renderer='json',
        permission='user',
        request_method='POST',
)
def set_url_require_login(request):
    key = request.json_body.get("key")
    require_login = request.json_body.get("require_login")
    if key is None:
        error.expected_error('missing "key" param')
    if require_login is None:
        error.expected_error('missing "require_login" param')

    _make_sp_request(get_rpc_stub(request).set_url_require_login, (key, require_login))

    return {}


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
        error.expected_error('missing "key" param')
    if expires is None:
        error.expected_error('missing "expires" param')

    expires_abs_milli = None
    try:
        expires_abs_milli = _delta_seconds_to_abs_milli(int(expires))
    except ValueError:
        error.expected_error('"expires" param must be a valid number')

    _make_sp_request(get_rpc_stub(request).set_url_expires, (key, expires_abs_milli))

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
        error.expected_error('missing "key" param')
    _make_sp_request(get_rpc_stub(request).remove_url_expires, (key))

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
        error.expected_error('missing "key" param')
    _make_sp_request(get_rpc_stub(request).remove_url, (key))

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
        error.expected_error('missing "key" param')
    if password is None:
        error.expected_error('missing "password" param')
    password = password.encode('utf-8')
    _make_sp_request(get_rpc_stub(request).set_url_password, (key, password))

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
        error.expected_error('missing "key" param')
    _make_sp_request(get_rpc_stub(request).remove_url_password, (key))

    return {}


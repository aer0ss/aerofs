import logging
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPNoContent, HTTPFound

from web.util import get_rpc_stub
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from add_mobile_device_view import is_mobile_supported


log = logging.getLogger(__name__)

# URL param keys
_URL_PARAM_DEVICE_ID = 'device_id' # the value is a HEX encoded device id
_URL_PARAM_DEVICE_NAME = 'device_name'

@view_config(
    route_name = 'my_devices',
    permission = 'user',
    renderer='devices.mako'
)
def my_devices(request):
    _ = request.translate

    user = authenticated_userid(request)

    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download', _query={'msg_type':'no_device'}))

    return _devices(request, devices, user, _("My devices"), False, True)

@view_config(
    route_name = 'user_devices',
    permission = 'admin',
    renderer='devices.mako'
)
def user_devices(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]

    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    return _devices(request, devices, user, _("${name}'s devices", {'name': full_name}), False, False)

@view_config(
    route_name = 'team_server_devices',
    permission = 'admin',
    renderer='devices.mako'
)
def team_server_devices(request):
    _ = request.translate

    sp = get_rpc_stub(request)
    ts_user = sp.get_team_server_user_id().id
    devices = sp.list_user_devices(ts_user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download_team_server', _query={'msg_type':'no_device'}))

    return _devices(request, devices, ts_user, _("Team Servers"), True, False)

def _devices(request, devices, user, page_heading, are_team_servers, show_add_mobile_device):
    return {
        'url_param_user': URL_PARAM_USER,
        'url_param_device_id': _URL_PARAM_DEVICE_ID,
        'url_param_device_name': _URL_PARAM_DEVICE_NAME,
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'user': user,
        'devices': devices,
        'are_team_servers': are_team_servers,
        'show_add_mobile_device': show_add_mobile_device and is_mobile_supported(request.registry.settings),
    }

@view_config(
    route_name = 'json.rename_device',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_rename_device(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]
    device_id = _get_device_id_from_request(request, 'rename')
    device_name = request.params[_URL_PARAM_DEVICE_NAME]

    sp = get_rpc_stub(request)
    sp.set_user_preferences(user, None, None, device_id, device_name)
    return HTTPNoContent()

@view_config(
    route_name = 'json.unlink_device',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_unlink_device(request):
    _ = request.translate

    device_id = _get_device_id_from_request(request, 'unlink')

    sp = get_rpc_stub(request)
    # Erase: False.
    sp.unlink_device(device_id, False)
    return HTTPNoContent()

@view_config(
    route_name = 'json.erase_device',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_erase_device(request):
    _ = request.translate

    device_id = _get_device_id_from_request(request, 'erase')

    sp = get_rpc_stub(request)
    # Erase: True.
    sp.unlink_device(device_id, True)
    return HTTPNoContent()

def _get_device_id_from_request(request, action):
    """
    N.B. This method assumes the parameter value is a HEX encoded device id
    """
    device_id = request.params[_URL_PARAM_DEVICE_ID]
    log.info('{} {} device {}'.format(authenticated_userid(request), action, device_id))
    return device_id.decode('hex')

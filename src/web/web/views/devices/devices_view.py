import logging
from web.util import *
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPNoContent, HTTPFound
from ..team_members.team_members_view import URL_PARAM_USER, URL_PARAM_FULL_NAME

log = logging.getLogger("web")

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

    user = get_session_user(request)

    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download') + "?msg_type=no_device")

    return _devices(devices, user, _("Devices"))

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

    return _devices(devices, user, _("${name}'s Devices", {'name': full_name}))

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
        raise HTTPFound(request.route_path('download_team_server') + "?msg_type=no_device")

    return _devices(devices, ts_user, _("Team Servers"))

def _devices(devices, user, page_title):
    return {
        'url_param_user': URL_PARAM_USER,
        'url_param_device_id': _URL_PARAM_DEVICE_ID,
        'url_param_device_name': _URL_PARAM_DEVICE_NAME,
        'page_title': page_title,
        'user': user,
        'devices': devices
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
    log.info('{} {} device {}'.format(get_session_user(request), action, device_id))
    return device_id.decode('hex')
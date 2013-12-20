from datetime import datetime
import logging
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPNoContent, HTTPFound, HTTPInternalServerError
import requests

from web.util import get_rpc_stub
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from add_mobile_device_view import is_mobile_supported


log = logging.getLogger(__name__)

# URL param keys
_URL_PARAM_DEVICE_ID = 'device_id'  # the value is a HEX encoded device id
_URL_PARAM_DEVICE_NAME = 'device_name'


@view_config(
    route_name='my_devices',
    permission='user',
    renderer='devices.mako'
)
def my_devices(request):
    _ = request.translate

    user = authenticated_userid(request)

    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download', _query={'msg_type': 'no_device'}))

    return _devices(request, devices, user, _("My devices"), False, True)


@view_config(
    route_name='user_devices',
    permission='admin',
    renderer='devices.mako'
)
def user_devices(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]

    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    return _devices(request, devices, user, _("${name}'s devices", {'name': full_name}), False,
                    False)


@view_config(
    route_name='team_server_devices',
    permission='admin',
    renderer='devices.mako'
)
def team_server_devices(request):
    _ = request.translate

    sp = get_rpc_stub(request)
    ts_user = sp.get_team_server_user_id().id
    devices = sp.list_user_devices(ts_user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download_team_server',
                                           _query={'msg_type': 'no_device'}))

    return _devices(request, devices, ts_user, _("Team Servers"), True, False)


def _devices(request, devices, user, page_heading, are_team_servers, show_add_mobile_device):
    try:
        last_seen_nullable = _get_last_seen(_get_devman_url(request.registry.settings), devices)
    except Exception as e:
        log.error('get_last_seen failed. use null values: {}'.format(e))
        last_seen_nullable = None

    return {
        'url_param_user': URL_PARAM_USER,
        'url_param_device_id': _URL_PARAM_DEVICE_ID,
        'url_param_device_name': _URL_PARAM_DEVICE_NAME,
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'user': user,
        'devices': devices,
        # The value is None if the devman service throws
        'last_seen_nullable': last_seen_nullable,
        'are_team_servers': are_team_servers,
        'show_add_mobile_device': show_add_mobile_device and is_mobile_supported(
            request.registry.settings),
    }


# See aerofs/src/devman/README.txt for the API spec
_devman_polling_interval = None


def _get_devman_url(settings):
    # In private deployment, we get the URL from the configuration service. In prod, use the
    # default.
    return settings.get("base.devman.url", "http://sp.aerofs.com:9020")


def _get_devman_polling_interval(devman_url):
    """
    Return the polling interval of the devman service.
    """
    global _devman_polling_interval
    if not _devman_polling_interval:
        r = requests.get(devman_url + '/polling_interval')
        _throw_on_devman_error(r)
        _devman_polling_interval = int(r.text)
    return _devman_polling_interval


def _get_last_seen(devman_url, devices):
    """
    Call the devman service to retrieve last seen time and location for the list of
    devices.
    @return a dict of:

        { "<device_id>": { "time": "Just Now", "ip": "1.2.3.4" }, ... }

        Devices that haven't been seen before are abscent from the map.
    """

    ret = {}
    for device in devices:
        did = device.device_id.encode('hex')
        r = requests.get(devman_url + '/devices/' + did)
        # The device is never seen. skip
        if r.status_code == 404:
            continue

        _throw_on_devman_error(r)

        json = r.json()
        ret[did] = {
            'time': _pretty_timestamp(_get_devman_polling_interval(devman_url),
                                      json['last_seen_time'] / 1000),
            'ip': json['ip_address']
        }

    return ret


def _pretty_timestamp(polling_interval, timestamp):
    """
    Format the timestamp beautifully
    @param timestamp the given timestamp in seconds

    http://stackoverflow.com/questions/1551382/user-friendly-time-format-in-python
    """

    diff = datetime.now() - datetime.fromtimestamp(timestamp)
    second_diff = diff.seconds
    day_diff = diff.days

    if day_diff < 0:
        return ''

    if day_diff == 0:
        if second_diff < polling_interval:
            return "just now"
        if second_diff < 60:
            return str(second_diff) + " seconds ago"
        if second_diff < 120:
            return "a minute ago"
        if second_diff < 3600:
            return str(second_diff / 60) + " minutes ago"
        if second_diff < 7200:
            return "an hour ago"
        if second_diff < 86400:
            return str(second_diff / 3600) + " hours ago"
    if day_diff == 1:
        return "yesterday"
    if day_diff < 7:
        return str(day_diff) + " days ago"
    if day_diff < 31:
        return str(day_diff / 7) + " weeks ago"
    if day_diff < 365:
        return str(day_diff / 30) + " months ago"
    return str(day_diff / 365) + " years ago"


def _throw_on_devman_error(r):
    if not r.ok:
        msg = 'devman returns {}: {}'.format(r.status_code, r.text)
        log.error(msg)
        raise HTTPInternalServerError(msg)


@view_config(
    route_name='json.rename_device',
    renderer='json',
    permission='user',
    request_method='POST'
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
    route_name='json.unlink_device',
    renderer='json',
    permission='user',
    request_method='POST'
)
def json_unlink_device(request):
    _ = request.translate

    device_id = _get_device_id_from_request(request, 'unlink')

    sp = get_rpc_stub(request)
    # Erase: False.
    sp.unlink_device(device_id, False)
    return HTTPNoContent()


@view_config(
    route_name='json.erase_device',
    renderer='json',
    permission='user',
    request_method='POST'
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

from datetime import datetime
from base64 import b64encode, b64decode
from urllib import quote
import json
import logging
import time
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPNoContent, HTTPBadRequest, HTTPFound, HTTPInternalServerError
import requests

from web.analytics import send_analytics_event
from web.util import get_rpc_stub, get_deployment_secret
from web.oauth import get_privileged_bifrost_client, is_aerofs_mobile_client_id
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME


log = logging.getLogger(__name__)


@view_config(
    route_name='my_devices',
    permission='user',
    renderer='devices.mako'
)
def my_devices(request):
    send_analytics_event(request, "ACTIVE_USER")
    _ = request.translate

    user = authenticated_userid(request)

    # if there aren't any devices, redirect user to download page
    device_data = get_devices_for_user(request, user)
    if len(device_data['devices']) + len(device_data['mobile_devices']) == 0:
        return HTTPFound(request.route_path('download', _query={'msg_type': 'no_device'}))

    return _device_page(request, user, _("My devices"), False)


@view_config(
    route_name='user_devices',
    permission='admin',
    renderer='devices.mako'
)
def user_devices(request):
    send_analytics_event(request, "ACTIVE_USER")
    _ = request.translate
    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]
    return _device_page(request, user, _("${name}'s devices", {'name': full_name}), False)


@view_config(
    route_name='team_server_devices',
    permission='admin',
    renderer='devices.mako'
)
def team_server_devices(request):
    send_analytics_event(request, "ACTIVE_USER")
    _ = request.translate

    sp = get_rpc_stub(request)
    ts_user = sp.get_team_server_user_id().id
    devices = sp.list_user_devices(ts_user).device

    if len(devices) == 0:
        raise HTTPFound(request.route_path('download_team_server',
                                           _query={'msg_type': 'no_device'}))

    return _device_page(request, ts_user, _("Team Servers"), True)


def _device_page(request, user, page_heading, are_team_servers):
    return {
        'url_param_user': URL_PARAM_USER,
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'user': user,
        'are_team_servers': are_team_servers,
    }


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
            if second_diff > 1:
                return str(second_diff) + " seconds ago"
            else:
                return "a second ago"
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
        if day_diff / 7 > 1:
            return str(day_diff / 7) + " weeks ago"
        else:
            return "a week ago"
    if day_diff < 365:
        if day_diff / 30 > 1:
            return str(day_diff / 30) + " months ago"
        else:
            return "a month ago"
    if day_diff / 365 > 1:
        return str(day_diff / 365) + " years ago"
    else:
        return "a year ago"

def _jsonable_device(device, last_seen_data):
    '''Converts a non-mobile device protobuf thing into a JSON-compatible dictionary.'''
    encoded_id = device.device_id.encode('hex')
    if encoded_id in last_seen_data:
        last_seen = last_seen_data[encoded_id]['time']
        ip = last_seen_data[encoded_id]['ip']
    else:
        last_seen = None
        ip = None
    try:
        client_id = device.client_id
    except AttributeError:
        client_id = None
    return {
            'name': device.device_name,
            'os_family': device.os_family,
            'os_name': device.os_name,
            'is_mobile': device.os_family in ['iOS', 'Android'],
            'id': encoded_id,
            'last_seen': last_seen,
            'ip': ip,
            'client_id': client_id
        }

def _jsonable_device_mobile(device):
    '''Converts a mobile device protobuf thing into a JSON-compatible dictionary.'''
    return {
            'token': device['token'],
            'client_id': device['client_id'],
            'os_name': 'Android' if device['client_id'] == 'aerofs-android' else 'iOS',
            'is_mobile': True,
            'last_seen': None,
            'ip': None,
        }

@view_config(
    route_name='json.get_devices',
    renderer='json',
    http_cache = 0,
    permission='user',
    request_method='GET'
)
def json_get_devices(request):
    authenticated_userid(request)
    user = request.params["user"]
    return get_devices_for_user(request, user)

def get_devices_for_user(request, user):
    sp = get_rpc_stub(request)
    devices = sp.list_user_devices(user).device

    # Note well: the access check here depends on having first made the above
    # list_user_devices call against SP, which will refuse if the requester is
    # not permitted to ask about the target user.  Bifrost currently lacks
    # access controls for this.
    bifrost_client = get_privileged_bifrost_client(request, service_name="web")
    r = bifrost_client.get_access_tokens_for(user)
    bifrost_client.raise_on_error()
    mobile_devices = [t for t in r.json()["tokens"] if is_aerofs_mobile_client_id(t["client_id"])]

    deployment_secret = get_deployment_secret(request.registry.settings)

    last_seen_data = {}
    try:
        last_seen_data = _get_last_seen_data(deployment_secret, user, devices)
    except Exception as e:
        log.error('get_last_seen failed. use null values: {}'.format(e))

    device_list = []
    for d in devices:
        device_list.append(_jsonable_device(d, last_seen_data))
    mobile_device_list = []
    for d in mobile_devices:
        mobile_device_list.append(_jsonable_device_mobile(d))
    return {
        'devices': device_list,
        'mobile_devices': mobile_device_list,
    }


@view_config(
    route_name='json.rename_device',
    renderer='json',
    permission='user',
    request_method='POST'
)
def json_rename_device(request):
    device_id = _get_device_id_from_request(request, 'rename')
    device_name = request.json_body['device_name']
    device_owner = request.json_body['device_owner']

    sp = get_rpc_stub(request)
    sp.set_user_preferences(device_owner, None, None, device_id, device_name)
    return HTTPNoContent()


@view_config(
    route_name='json.unlink_device',
    renderer='json',
    permission='user',
    request_method='POST'
)
def json_unlink_device(request):
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
    device_id = _get_device_id_from_request(request, 'erase')

    sp = get_rpc_stub(request)
    # Erase: True.
    sp.unlink_device(device_id, True)
    return HTTPNoContent()


def _get_device_id_from_request(request, action):
    """
    N.B. This method assumes the parameter value is a HEX encoded device id
    """
    try:
        device_id = request.params['device_id']
    except KeyError:
        device_id = request.json_body['device_id']
    log.info('{} {} device {}'.format(authenticated_userid(request), action, device_id))
    decoded_id = device_id.decode('hex')
    if len(decoded_id) != 16:
        msg = 'hex decoded device id "{}" is an invalid length'.format(decoded_id)
        log.error(msg)
        raise HTTPBadRequest(msg)
    return decoded_id


def _get_last_seen_data(deployment_secret, user_id, devices):
    """
    Call charlie to retrieve last seen time and location for the given list of devices.
    @return a dict of:

        { "<device_id>": { "time": "Just Now", "ip": "1.2.3.4" }, ... }

    Devices that haven't been seen before are absent from the map.
    """

    last_seen = {}
    for device in devices:
        did = device.device_id.encode('hex')
        url = 'http://charlie.service:8701/checkin/{}'.format(did)
        auth_headers = {
            "Authorization": "Aero-Service-Shared-Secret {} {}".format(
                "web",
                deployment_secret
                )
        }
        r = requests.get(url, headers=auth_headers)

        # device was never seen - ignore it
        if r.status_code == requests.codes.not_found:
            continue

        if not r.ok:
            msg = 'charlie returns {}: {}'.format(r.status_code, r.text)
            log.error(msg)
            raise HTTPInternalServerError(msg)

        device_last_seen = r.json()
        last_seen_time = datetime.strptime(device_last_seen['time'], '%Y-%m-%dT%H:%M:%SZ')
        log.info('last_seen:{}'.format(last_seen_time))

        last_seen[did] = {
            'time': _pretty_timestamp(60, time.mktime(last_seen_time.timetuple())),
            'ip': device_last_seen['ip'],
        }

    return last_seen

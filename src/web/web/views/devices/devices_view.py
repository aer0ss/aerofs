from datetime import datetime
from base64 import b64decode
from urllib import quote
import json
import logging
import time
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPNoContent, HTTPFound, HTTPInternalServerError
import requests

from web.util import get_rpc_stub
from web.oauth import get_bifrost_url, is_aerofs_mobile_client_id
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME


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

    r = requests.get(get_bifrost_url(request) + '/tokenlist',
            params={'owner': authenticated_userid(request)})
    r.raise_for_status()
    mobile_devices = [t for t in r.json()["tokens"] if is_aerofs_mobile_client_id(t["client_id"])]

    if len(devices) + len(mobile_devices) == 0:
        raise HTTPFound(request.route_path('download', _query={'msg_type': 'no_device'}))

    return _devices(request, devices, mobile_devices, user, _("My devices"), False)


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

    r = requests.get(get_bifrost_url(request) + '/tokenlist', params={'owner': user})
    r.raise_for_status()
    mobile_devices = [t for t in r.json()["tokens"] if is_aerofs_mobile_client_id(t["client_id"])]

    return _devices(request, devices, mobile_devices, user, _("${name}'s devices", {'name': full_name}), False)


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

    mobile_devices = []

    return _devices(request, devices, mobile_devices, ts_user, _("Team Servers"), True)


def _devices(request, devices, mobile_devices, user, page_heading, are_team_servers):
    last_seen_nullable = None
    try:
        last_seen_nullable = _get_last_seen(_get_verkehr_url(request.registry.settings), devices)
    except Exception as e:
        log.error('get_last_seen failed. use null values: {}'.format(e))

    return {
        'url_param_user': URL_PARAM_USER,
        'url_param_device_id': _URL_PARAM_DEVICE_ID,
        'url_param_device_name': _URL_PARAM_DEVICE_NAME,
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'user': user,
        'devices': devices,
        'last_seen_nullable': last_seen_nullable,
        'mobile_devices': mobile_devices,
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


@view_config(
    route_name='json.rename_device',
    renderer='json',
    permission='user',
    request_method='POST'
)
def json_rename_device(request):
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
    device_id = request.params[_URL_PARAM_DEVICE_ID]
    log.info('{} {} device {}'.format(authenticated_userid(request), action, device_id))
    return device_id.decode('hex')


def _get_verkehr_url(settings):
   host = settings.get('base.verkehr.host', 'verkehr.aerofs.com')
   port = settings.get('base.verkehr.port.rest', 25234)
   return 'http://{}:{}'.format(host, port)


def _get_last_seen(verkehr_url, devices):
    """
    Call verkehr to retrieve last seen time and location for the given list of devices.
    @return a dict of:

        { "<device_id>": { "time": "Just Now", "ip": "1.2.3.4" }, ... }

        Devices that haven't been seen before are absent from the map.
    """

    last_seen = {}
    for device in devices:
        did = device.device_id.encode('hex')

        last_seen_topic = quote('online/{}'.format(did), safe='') # by setting safe to empty we also quote '/'
        r = requests.get('{}/v2/topics/{}/updates'.format(verkehr_url, last_seen_topic), params={'from': '-1'})

        # device was never seen - ignore it
        if r.status_code == requests.codes.not_found:
            continue

        if not r.ok:
            msg = 'verkehr returns {}: {}'.format(r.status_code, r.text)
            log.error(msg)
            raise HTTPInternalServerError(msg)

        updates_container = r.json()
        updates = updates_container['updates']

        # the topic exists but no updates are available (?!)
        if len(updates) == 0:
            continue

        payload = updates[0]['payload'] # should only be one entry
        device_last_seen = json.loads(b64decode(payload))
        last_seen_time = datetime.strptime(device_last_seen['time'], '%Y-%m-%dT%H:%M:%SZ')
        log.info('last_seen:{}'.format(last_seen_time))

        last_seen[did] = {
            'time': _pretty_timestamp(60, time.mktime(last_seen_time.timetuple())),
            'ip': device_last_seen['ip'],
        }

    return last_seen

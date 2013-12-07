import logging
import json
from pyramid.httpexceptions import HTTPFound
import requests

from pyramid.view import view_config
from web import util
from web.util import flash_error, flash_success

_BIFROST_BASE_URL = "http://localhost:8700"

log = logging.getLogger("web")


@view_config(
    route_name='apps',
    permission='maintain',
    renderer='apps/apps.mako',
    request_method='GET'
)
def apps(request):
    r = requests.get(_BIFROST_BASE_URL + '/clients')
    if r.ok:
        # clients is an array of registered clients
        clients = r.json()['clients']
        # skip built-in apps e.g. 'aerofs-ios'
        clients = [cli for cli in clients if len(cli['client_id']) == 36]
    else:
        clients = []
        _flash_error_for_response(request, r)

    return {
        'clients': clients
    }


@view_config(
    route_name='register_app',
    permission='maintain',
    renderer='apps/register_app.mako',
    request_method='GET'
)
def register_app_get(request):
    return {}


@view_config(
    route_name='register_app',
    permission='maintain',
    request_method='POST'
)
def register_app_post(request):
    client_name = request.params.get('client_name', '')
    if not client_name:
        flash_error(request, 'The application name is required.')
        raise HTTPFound(request.route_path('register_app'))

    r = requests.post(_BIFROST_BASE_URL + '/clients', data = {
        'resource_server_key': 'oauth-havre',
        'client_name': client_name,
        'redirect_uri': request.params.get('redirect_uri')
    })
    if not r.ok:
        _flash_error_for_response(request, r)
        raise HTTPFound(request.route_path('register_app'))

    flash_success(request, 'The application is registered.')
    return HTTPFound(request.route_path('apps'))


@view_config(
    route_name='json_delete_app',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_delete_app(request):
    r = requests.delete(_BIFROST_BASE_URL + '/clients/{}'
            .format(request.params['client_id']))
    if not r.ok:
        util.error(_get_error_message_for_resonse(r))
    return {}


def _flash_error_for_response(request, response):
    flash_error(request, _get_error_message_for_resonse(response))


def _get_error_message_for_resonse(response):
    log.error('bifrost error: {} {}'.format(response.status_code, response.text))
    return 'An error occurred. Please try again. The error is: {} {}'\
            .format(response.status_code, response.text)
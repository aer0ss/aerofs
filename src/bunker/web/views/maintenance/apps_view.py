import logging
from pyramid.httpexceptions import HTTPFound
import requests

from pyramid.view import view_config
from web import util
from web.util import flash_error, flash_success
from web.oauth import raise_error_for_bifrost_response, flash_error_for_bifrost_response, \
    is_builtin_client_id, get_bifrost_url, is_valid_non_builtin_client_id


log = logging.getLogger(__name__)


@view_config(
    route_name='registered_apps',
    permission='maintain',
    renderer='apps/apps.mako',
    request_method='GET'
)
def apps(request):
    r = requests.get(get_bifrost_url(request) + '/clients')
    if r.ok:
        # clients is an array of registered clients
        clients = r.json()['clients']
        # skip built-in apps
        clients = [cli for cli in clients if not is_builtin_client_id(cli['client_id'])]
    else:
        clients = []
        flash_error_for_bifrost_response(request, r)

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
    client_name = request.params.get('client_name')
    redirect_uri = request.params.get('redirect_uri')

    if client_name is None:
        flash_error(request, 'The application name is required.')
        raise HTTPFound(request.route_path('register_app'))

    if redirect_uri is None:
        flash_error(request, 'The redirect URI is required.')
        raise HTTPFound(request.route_path('register_app'))

    r = requests.post(get_bifrost_url(request) + '/clients', data = {
        'resource_server_key': 'oauth-havre',
        'client_name': client_name,
        'redirect_uri': redirect_uri
    })
    if not r.ok:
        flash_error_for_bifrost_response(request, r)
        raise HTTPFound(request.route_path('register_app'))

    flash_success(request, 'The application is registered.')
    return HTTPFound(request.route_path('registered_apps'))


@view_config(
    route_name='json_delete_app',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_delete_app(request):
    client_id = request.params['client_id']
    # This is to prevent injection attacks e.g. clients='../tokens'
    if not is_valid_non_builtin_client_id(client_id):
        log.error('json_delete_app(): invalid client_id: ' + client_id)
        util.error('The application ID is invalid.')

    r = requests.delete(get_bifrost_url(request) + '/clients/{}'
            .format(client_id))
    if not r.ok:
        raise_error_for_bifrost_response(r)
    return {}

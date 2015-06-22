import logging

from pyramid.httpexceptions import HTTPFound
from pyramid.view import view_config

from web import util
from web.util import flash_error, flash_success
from web.oauth import get_privileged_bifrost_client, is_builtin_client_id, is_valid_non_builtin_client_id
from maintenance_util import get_conf

log = logging.getLogger(__name__)


@view_config(
    route_name='registered_apps',
    permission='maintain',
    renderer='apps/registered_apps.mako',
    request_method='GET'
)
def registered_apps(request):
    bifrost_client = get_privileged_bifrost_client(request)
    r = bifrost_client.get_registered_apps()
    if r.ok:
        # clients is an array of registered clients
        clients = r.json()['clients']
        # skip built-in apps
        clients = [cli for cli in clients if not is_builtin_client_id(cli['client_id'])]
    else:
        clients = []
        bifrost_client.flash_on_error(request, r)

    conf = get_conf(request)
    return {
        'clients': clients,
        'hostname': conf['base.host.unified'],
        'cert': conf['server.browser.certificate']
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

    bifrost_client = get_privileged_bifrost_client(request)
    r = bifrost_client.register_app(client_name, redirect_uri)
    if not r.ok:
        bifrost_client.flash_on_error(request, r)
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
        util.expected_error('The application ID is invalid.')

    bifrost_client = get_privileged_bifrost_client(request)
    bifrost_client.delete_app(client_id)
    bifrost_client.raise_on_error()
    return {}

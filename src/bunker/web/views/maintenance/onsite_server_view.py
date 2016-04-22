import logging
import requests

from pyramid.httpexceptions import HTTPFound
from pyramid.view import view_config
from web.util import flash_error, flash_success
from web.views.maintenance.maintenance_util import get_conf, get_conf_client, \
    is_ipv4_address, is_hostname_resolvable


log = logging.getLogger(__name__)

_PARAM_SERVER = "prv-registry"
LOADER_URL = 'http://loader.service/v1'

@view_config(
    route_name='onsite_server',
    permission='maintain',
    renderer='onsite_server.mako',
    request_method='GET',
)
def onsite_server(request):
    conf = get_conf(request)
    r = requests.get(LOADER_URL + '/repo')
    r.raise_for_status()

    return {
        'server': r.json().get('repo', ""),
    }

@view_config(
    route_name='onsite_server',
    permission='maintain',
    request_method='POST',
)
def onsite_server_post(request):
    server = request.params[_PARAM_SERVER]
    # Validation: check that one of the following is true:
    #    1) the server is empty
    #    2) the server is a valid IPv4 address
    #    3) the server is a resolvable hostname
    if server == "" or is_ipv4_address(server) or is_hostname_resolvable(server):
        r = requests.post(LOADER_URL + '/repo/{}'.format(server))
        if r.status_code != 200:
            flash_error(request, "Could not set {} as your registry. Please contact support@aerofs.com".format(server))
        else:
            flash_success(request, "Saved {} as your registry.".format(server))
        return HTTPFound(location=request.route_path('onsite_server'))
    else:
        flash_error(request, "Could not resolve {}.".format(server))
        return HTTPFound(location=request.route_path('onsite_server'))

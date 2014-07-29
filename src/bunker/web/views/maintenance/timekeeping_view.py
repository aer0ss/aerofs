import logging

from pyramid.httpexceptions import HTTPFound
from pyramid.view import view_config

from web.util import flash_error, flash_success
from aerofs_common.bootstrap import BootstrapClient
from web.views.maintenance.maintenance_util import get_conf, get_conf_client, \
        is_ipv4_address, is_hostname_resolvable

log = logging.getLogger(__name__)

_PARAM_SERVER = "ntp-server"

@view_config(
    route_name='timekeeping',
    permission='maintain',
    renderer='timekeeping.mako',
    request_method='GET',
)
def timekeeping(request):
    conf = get_conf(request)
    server = conf.get("ntp.server", "")
    return {
        'server': server,
    }

@view_config(
    route_name='timekeeping',
    permission='maintain',
    request_method='POST',
)
def timekeeping_post(request):
    server = request.params[_PARAM_SERVER]
    # Validation: check that one of the following is true:
    #    1) the server is empty
    #    2) the server is a valid IPv4 address
    #    3) the server is a resolvable hostname
    if server == "" or is_ipv4_address(server) or is_hostname_resolvable(server):
        # save config to config server
        conf_client = get_conf_client(request)
        conf_client.set_external_property('ntp_server', server)
        # Submit bootstrap task to:
        #    1) rerender /etc/ntp.conf
        #    2) stop ntpd (to release the NTP socket)
        #    3) run ntpdate once, and
        #    4) start ntpd
        bootstrap_client = BootstrapClient(request.registry.settings["deployment.bootstrap_server_uri"])
        bootstrap_client.enqueue_task_set("reconfigure-ntp")
        flash_success(request, "Saved time server settings.")
        return HTTPFound(location=request.route_path('timekeeping'))
    else:
        flash_error(request, "Could not resolve {}.".format(server))
        return HTTPFound(location=request.route_path('timekeeping'))

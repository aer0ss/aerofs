import logging
from pyramid.view import view_config
from web.util import str2bool
from maintenance_util import get_conf, get_conf_client

log = logging.getLogger(__name__)

@view_config(
    route_name='link_settings',
    permission='maintain',
    renderer='link_settings.mako',
    request_method='GET'
)
def link_settings_view(request):
    conf = get_conf(request)
    links_require_login = conf.get("links_require_login.enabled", False)
    return {
        'links_require_login': str2bool(links_require_login)
    }

@view_config(
    route_name='json_set_require_login',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def json_set_require_login_post(request):
    links_require_login = str2bool(request.params.get("links_require_login"))

    conf_client = get_conf_client(request)
    conf_client.set_external_property('links_require_login_enabled', links_require_login)
    return {}

import logging
from pyramid.view import view_config
from web.util import str2bool
from maintenance_util import get_conf, get_conf_client

log = logging.getLogger(__name__)

@view_config(
    route_name='session_management',
    permission='maintain',
    renderer='session_management.mako',
    request_method='GET'
)
def session_management(request):
    conf = get_conf(request)
    enable_daily_expiration = conf.get("web.session_daily_expiration", False)
    return {
        'enable_daily_expiration': str2bool(enable_daily_expiration)
    }

@view_config(
    route_name='session_management',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def session_management_post(request):
    #Verify the state of the checkbox.
    enable_daily_expiration_checkbox = request.params.getall("daily-session-expiration")

    enable_daily_expiration = True if enable_daily_expiration_checkbox else False

    conf_client = get_conf_client(request)
    conf_client.set_external_property('web_session_daily_expiration', enable_daily_expiration)
    return {}

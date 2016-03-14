from pyramid.view import view_config
import logging


log = logging.getLogger(__name__)


@view_config(
    route_name='onsite_log_server',
    permission='maintain',
    renderer='onsite_log_server.mako',
    request_method='GET',
)
def onsite_log_server(request):
    return {}

from pyramid.view import view_config
from pyramid.security import NO_PERMISSION_REQUIRED
import logging

log = logging.getLogger(__name__)


@view_config(
    route_name='dashboard_home',
    permission=NO_PERMISSION_REQUIRED,
    renderer='maintenance_mode.mako'
)
def maintenance_mode(request):
    # Return status 503 Service Unavailable
    request.response.status = 503
    return {}

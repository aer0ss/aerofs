import logging

from pyramid.view import view_config

from web.analytics import send_analytics_event
from web.util import get_rpc_stub

log = logging.getLogger(__name__)

@view_config(
    route_name='licensing',
    renderer='licensing.mako',
    permission='admin',
    request_method='GET',
)
def org_settings(request):
    send_analytics_event(request, "ACTIVE_USER")

    sp = get_rpc_stub(request)

    reply = sp.get_org_preferences()

    return {
        'test': 'testing',
        'license_seats': int(request.registry.settings["license_seats"]),
        'license_seats_used': reply.license_seats_used,
        'external_user_count': reply.external_user_count,
    }

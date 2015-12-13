from pyramid.view import view_config
from web.views.maintenance.maintenance_util import get_conf
from web.util import str2bool
import logging


log = logging.getLogger(__name__)


def _is_email_ingegration_allowed(conf):
    # FIXME change to license_allow_enterprise_features. ENG-3372.
    return str2bool(conf.get('license_allow_auditing', True))


@view_config(
    route_name='email_integration',
    permission='maintain',
    renderer='email_integration.mako',
    request_method='GET',
)
def customization(request):
    conf = get_conf(request)
    if not _is_email_ingegration_allowed(conf):
        request.override_renderer = 'email_integration_upgrade.mako'
    return {}

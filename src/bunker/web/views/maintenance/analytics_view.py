import logging
import os
from pyramid.view import view_config
from web.util import str2bool
from maintenance_util import write_pem_to_file, \
    is_certificate_formatted_correctly, format_pem, get_conf, \
    get_conf_client, unformat_pem
from web.error import expected_error

log = logging.getLogger(__name__)

@view_config(
    route_name='analytics',
    permission='maintain',
    renderer='analytics.mako'
)
def analytics(request):
    conf = get_conf(request)

    endpoint = conf['analytics.endpoint']
    segment_endpoint = 'https://api.segment.io'
    enabled = False

    return {
        'segment_endpoint': segment_endpoint,
        'is_analytics_enabled': enabled,
        'use_custom_endpoint': endpoint != segment_endpoint and enabled,
        'analytics_endpoint': endpoint,
    }

@view_config(
    route_name='json_set_analytics',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_analytics(request):
    """
    N.B. the changes won't take effcts on the system until relevant services are
    restarted.
    """

    raise Exception("not supported")


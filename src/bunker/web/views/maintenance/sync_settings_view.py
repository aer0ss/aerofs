import logging
import os
from pyramid.view import view_config
from web.util import str2bool, get_settings_nonempty
from maintenance_util import write_pem_to_file, \
    is_certificate_formatted_correctly, format_pem, get_conf, \
    get_conf_client, unformat_pem
from web.error import expected_error

log = logging.getLogger(__name__)


@view_config(
    route_name='sync_settings',
    permission='maintain',
    renderer='sync_settings.mako'
)
def sync(request):
    conf = get_conf(request)

    return {
        'is_lansync_enabled': _is_lansync_enabled(conf),
    }

def _is_lansync_enabled(conf):
    """
    @return whether the user has enabled auditing.
    """
    return str2bool(get_settings_nonempty(conf, 'base.lansync.enabled', True))

@view_config(
    route_name='json_set_sync_settings',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_sync(request):
    """
    N.B. the changes won't take effect until the client has been restarted.
    """
    enable_lansync = str2bool(request.params['enable-lansync'])
    config = get_conf_client(request)

    config.set_external_property('enable_lansync', enable_lansync)

    return {}

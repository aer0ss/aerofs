import logging
from pyramid.view import view_config
from web.util import str2bool, get_settings_nonempty
from maintenance_util import get_conf, get_conf_client
from web.error import expected_error

log = logging.getLogger(__name__)

MIN_PORT = 1025
MAX_PORT = 65535

@view_config(
    route_name='sync_settings',
    permission='maintain',
    renderer='sync_settings.mako'
)
def sync(request):
    conf = get_conf(request)
    device_port_range_low = conf.get('daemon.port.range.low', "")
    device_port_range_high = conf.get('daemon.port.range.high', "")

    return {
        'is_lansync_enabled': _is_lansync_enabled(conf),
        'is_custom_ports_enabled': _is_custom_ports_enabled(conf),
        'device_port_range_low': device_port_range_low,
        'device_port_range_high': device_port_range_high,
        'min_port': MIN_PORT,
        'max_port': MAX_PORT
    }

def _is_lansync_enabled(conf):
    """
    @return whether the user has enabled auditing.
    """
    return str2bool(get_settings_nonempty(conf, 'base.lansync.enabled', True))

def _is_custom_ports_enabled(conf):
    """
    @return whether the user enabled default desktop client ports
    """
    return str2bool(get_settings_nonempty(conf, 'base.custom.ports.enabled', False))

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
    enable_custom_ports = str2bool(request.params['enable-custom-ports'])
    port_range_high = (request.params['port-range-high']).strip()
    port_range_low = (request.params['port-range-low']).strip()
    config = get_conf_client(request)

    if not enable_custom_ports:
        config.set_external_property('daemon_port_range_low', MIN_PORT)
        config.set_external_property('daemon_port_range_high', MAX_PORT)
    else:
        try:
            port_range_low = int(port_range_low)
            port_range_high = int(port_range_high)
        except ValueError as e:
            log.warn("Invalid port values. The error is: " + str(e))
            expected_error("Invalid non-integer ports provided. Please specify values between " + str(MIN_PORT) +
                           " and " + str(MAX_PORT))

        if not ((MIN_PORT <= port_range_low <= MAX_PORT) and (MIN_PORT <= port_range_high <= MAX_PORT)):
                log.warn("Invalid port range. The specified range is out of bounds.")
                expected_error("Invalid port range. Please specify a range between " + str(MIN_PORT) +
                               " and " + str(MAX_PORT))

        if port_range_low > port_range_high:
                log.warn("Invalid port range: Lower bound port higher than upper bound port")
                expected_error("Invalid port range. Lower bound port cannot be higher than upper bound port.")

        config.set_external_property('daemon_port_range_low', port_range_low)
        config.set_external_property('daemon_port_range_high', port_range_high)

    config.set_external_property('enable_lansync', enable_lansync)
    config.set_external_property('enable_custom_daemon_port_range', enable_custom_ports)

    return {}

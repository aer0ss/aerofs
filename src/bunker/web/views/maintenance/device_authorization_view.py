import logging
import os
from pyramid.view import view_config
from web.util import str2bool
from maintenance_util import write_pem_to_file, \
    is_certificate_formatted_correctly, format_pem, get_conf, get_conf_client, \
    unformat_pem
from web.error import error

log = logging.getLogger(__name__)

@view_config(
    route_name='device_authorization',
    permission='maintain',
    renderer='device_authorization.mako'
)
def device_authorization(request):
    conf = get_conf(request)
    return {
        'device_authorization_endpoint_enabled':
            str2bool(conf['device.authorization.endpoint_enabled']),
        'device_authorization_endpoint_host':
            conf['device.authorization.endpoint_host'],
        'device_authorization_endpoint_port':
            conf['device.authorization.endpoint_port'],
        'device_authorization_endpoint_path':
            conf['device.authorization.endpoint_path'],
        'device_authorization_endpoint_use_ssl':
            str2bool(conf['device.authorization.endpoint_use_ssl']),
        'device_authorization_endpoint_certificate':
            unformat_pem(conf['device.authorization.endpoint_certificate']),
    }

@view_config(
    route_name='json_set_device_authorization',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_set_device_authorization(request):
    """
    N.B. the changes won't take effcts on the system until relevant services are
    restarted.
    """

    print request.params

    config = get_conf_client(request)

    enabled = str2bool(request.params['enabled'])
    host = request.params['host']
    port = request.params['port']
    path = request.params['path']
    # N.B. checkboxes are not submitted unless true.
    use_ssl = str2bool(request.params.get('use_ssl'))
    certificate = request.params['certificate']

    if enabled:
        if not host or not port:
            error('Please specify the hostname and port.')

    if use_ssl and len(certificate) > 0:
        certificate_filename = write_pem_to_file(certificate)
        try:
            is_certificate_valid = is_certificate_formatted_correctly(
                certificate_filename)
            if not is_certificate_valid:
                error("The certificate you provided is invalid.")
        finally:
            os.unlink(certificate_filename)

    config.set_external_property('device_authorization_endpoint_enabled', enabled)
    config.set_external_property('device_authorization_endpoint_host', host)
    config.set_external_property('device_authorization_endpoint_port', port)
    config.set_external_property('device_authorization_endpoint_path', path)
    config.set_external_property('device_authorization_endpoint_use_ssl', use_ssl)
    config.set_external_property('device_authorization_endpoint_certificate',
                                 format_pem(certificate))

    return {}

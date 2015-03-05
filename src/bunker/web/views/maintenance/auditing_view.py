import logging
import os
from pyramid.view import view_config
from web.util import str2bool
from maintenance_util import write_pem_to_file, \
    is_certificate_formatted_correctly, format_pem, get_conf, \
    get_conf_client, unformat_pem
from web.error import error

log = logging.getLogger(__name__)

@view_config(
    route_name='auditing',
    permission='maintain',
    renderer='auditing.mako'
)
def auditing(request):
    conf = get_conf(request)

    # Override template if the license forbids auditing:
    if not _is_audit_allowed(conf):
        request.override_renderer = 'auditing_upgrade.mako'

    return {
        'is_audit_enabled': _is_audit_enabled(conf),
        'downstream_host': conf['base.audit.downstream_host'],
        'downstream_port': conf['base.audit.downstream_port'],
        'downstream_ssl_enabled': str2bool(conf['base.audit.downstream_ssl_enabled']),
        'downstream_cert': unformat_pem(conf['base.audit.downstream_certificate']),
    }


def _is_audit_allowed(conf):
    """
    @return whether auditing support is included in the license
    """
    return str2bool(conf.get('license_allow_auditing', False))


def _is_audit_enabled(conf):
    """
    @return whether auditing support is included in the license AND the user has
    enabled auditing.
    """
    return _is_audit_allowed(conf) and \
        str2bool(conf.get('base.audit.enabled', False))


@view_config(
    route_name='json_set_auditing',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_audit(request):
    """
    N.B. the changes won't take effcts on the system until relevant services are
    restarted.
    """

    # _is_audit_allowed is needed to prevent users from bypassing the front-end
    # license enforcement and enable auditing by calling this method directly.
    audit_enabled = _is_audit_allowed(get_conf(request)) and \
        str2bool(request.params['audit-enabled'])

    config = get_conf_client(request)
    config.set_external_property('audit_enabled', audit_enabled)

    if not audit_enabled:
        config.set_external_property('audit_downstream_host', '')
        config.set_external_property('audit_downstream_port', '')
        config.set_external_property('audit_downstream_ssl_enabled', '')
        config.set_external_property('audit_downstream_certificate', '')
    else:
        audit_downstream_host = request.params['audit-downstream-host']
        audit_downstream_port = request.params['audit-downstream-port']
        audit_downstream_ssl_enabled = \
            str2bool(request.params.get('audit-downstream-ssl-enabled'))
        audit_downstream_certificate = \
            request.params['audit-downstream-certificate']

        if not audit_downstream_host or not audit_downstream_port:
            error('Please specify the hostname and port.')

        # TODO (MP) need better sanity checking on downstream system.

        # Check the validity of the certificate, if provided.
        if len(audit_downstream_certificate) > 0:
            certificate_filename = write_pem_to_file(
                audit_downstream_certificate)
            try:
                is_certificate_valid = is_certificate_formatted_correctly(
                    certificate_filename)
                if not is_certificate_valid:
                    error("The certificate you provided is invalid.")
            finally:
                os.unlink(certificate_filename)

        config.set_external_property('audit_downstream_host',
                                     audit_downstream_host)
        config.set_external_property('audit_downstream_port',
                                     audit_downstream_port)
        config.set_external_property('audit_downstream_ssl_enabled',
                                     audit_downstream_ssl_enabled)
        config.set_external_property('audit_downstream_certificate',
                                     format_pem(audit_downstream_certificate))
    return {}


import logging
import os

from pyramid.httpexceptions import HTTPOk
from pyramid.view import view_config
from web.error import error
from web.util import str2bool
from maintenance_util import format_pem, get_conf, get_conf_client, \
    is_certificate_formatted_correctly, write_pem_to_file

log = logging.getLogger(__name__)

_SUPPORT_ENABLED_PROP = 'base.dryad.enabled'
_SUPPORT_HOST_PROP = 'base.dryad.hostname'
_SUPPORT_PORT_PROP = 'base.dryad.port'
_SUPPORT_CERT_PROP = 'base.dryad.certificate'

_SUPPORT_ENABLED_EXTERNAL_PROP = 'dryad_enabled'
_SUPPORT_HOST_EXTERNAL_PROP = 'dryad_hostname'
_SUPPORT_PORT_EXTERNAL_PROP = 'dryad_port'
_SUPPORT_CERT_EXTERNAL_PROP = 'dryad_certificate'

_FORM_OPTION_FIELD = 'option'
_FORM_HOST_FIELD = 'hostname'
_FORM_PORT_FIELD = 'port'
_FORM_CERT_FIELD = 'certificate'

_OPTION_PUBLIC = 'public'
_OPTION_ON_SITE = 'on-site'
_OPTION_DISABLED = 'disabled'
_OPTIONS = [_OPTION_PUBLIC, _OPTION_ON_SITE, _OPTION_DISABLED]


@view_config(
    route_name='problem_reporting',
    permission='maintain',
    renderer='problem_reporting.mako',
)
def problem_reporting(request):
    conf = get_conf(request)

    enabled = conf[_SUPPORT_ENABLED_PROP]
    hostname = conf[_SUPPORT_HOST_PROP]
    port = conf[_SUPPORT_PORT_PROP]

    option = _OPTION_DISABLED if not str2bool(enabled) else \
        _OPTION_PUBLIC if hostname == '' else \
        _OPTION_ON_SITE

    return {
        'option': option,
        'hostname': hostname,
        'port': port,
    }


@view_config(
    route_name='json_set_problem_reporting_options',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def json_set_problem_reporting_options(request):
    log.info('setting problem reporting options')

    _validate_support_options(request.params)

    option = request.params[_FORM_OPTION_FIELD]

    if option == _OPTION_PUBLIC:
        enabled = True
        hostname = ''
        port = ''
        certificate = ''
    elif option == _OPTION_ON_SITE:
        enabled = True
        hostname = request.params[_FORM_HOST_FIELD]
        port = request.params[_FORM_PORT_FIELD]
        certificate = request.params[_FORM_CERT_FIELD]
    elif option == _OPTION_DISABLED:
        enabled = False
        hostname = ''
        port = ''
        certificate = ''
    else:
        ## In theory, we've already validated all supported options earlier.
        ## In practice, this is put in place to guard against future changes.
        error(_ERROR_NOT_SUPPORTED_OPTION)

    conf = get_conf_client(request)
    conf.set_external_property(_SUPPORT_ENABLED_EXTERNAL_PROP, enabled)
    conf.set_external_property(_SUPPORT_HOST_EXTERNAL_PROP, hostname)
    conf.set_external_property(_SUPPORT_PORT_EXTERNAL_PROP, port)
    conf.set_external_property(_SUPPORT_CERT_EXTERNAL_PROP,
                               format_pem(certificate))

    return HTTPOk()

_ERROR_NO_OPTION = 'Please select a reporting option.'
_ERROR_NO_HOSTNAME = 'Please specify the hostname of the on-site reporting ' \
                     'system.'
_ERROR_NO_PORT = 'Please specify the port of the on-site reporting system.'
_ERROR_NO_CERTIFICATE = 'Please specify the certificate of the on-site ' \
                        'reporting system.'
_ERROR_INVALID_OPTION = 'The option you have selected is invalid. Please ' \
                        'select a valid support option.'
_ERROR_NOT_SUPPORTED_OPTION = 'The option you have selected is not ' \
                              'supported. Please select a different option.'
_ERROR_INVALID_HOSTNAME = 'The hostname you have provided is invalid. Please ' \
                          'provide a valid hostname.'
_ERROR_INVALID_PORT = 'The port you have provided is invalid. Please provide ' \
                      'a valid port.'
_ERROR_INVALID_CERTIFICATE = 'The certificate you have provided is invalid. ' \
                             'Please provide a valid X509 SSL certificate ' \
                             'file in the PEM format.'


def _validate_support_options(params):
    if not _FORM_OPTION_FIELD in params:
        error(_ERROR_NO_OPTION)
    elif params[_FORM_OPTION_FIELD] not in _OPTIONS:
        error(_ERROR_INVALID_OPTION)

    if params[_FORM_OPTION_FIELD] == _OPTION_ON_SITE:
        if not _FORM_HOST_FIELD in params:
            error(_ERROR_NO_HOSTNAME)
        else:
            # TODO: validate hostname somehow
            pass

        if not _FORM_PORT_FIELD in params:
            error(_ERROR_NO_PORT)
        else:
            # TODO: validate the port somehow
            pass

        if not _FORM_CERT_FIELD in params:
            error(_ERROR_NO_CERTIFICATE)
        else:
            _validate_certificate(params[_FORM_CERT_FIELD])


def _validate_certificate(certificate):
    filename = write_pem_to_file(certificate)
    try:
        if not is_certificate_formatted_correctly(filename):
            error(_ERROR_INVALID_CERTIFICATE)
    finally:
        os.unlink(filename)

##
## The view related to log file colllection
##
import os
from datetime import datetime
from pyramid.response import Response
import logging, requests, uuid
from pyramid.view import view_config
from maintenance_util import get_conf, get_conf_client, write_pem_to_file, \
    is_certificate_formatted_correctly, format_pem, unformat_pem
from web.error import expected_error, unexpected_error
from web.version import get_private_version

log = logging.getLogger(__name__)

# The path is defined in BootstrapParam.java
LOG_ARCHIVE_PATH = '/opt/bootstrap/public/logs.zip'


@view_config(
    route_name='logs_auto_download',
    permission='maintain',
    renderer='logs_auto_download.mako'
)
def logs_auto_download(request):
    _log_customer_id(request)
    return {}


def _log_customer_id(request):
    log.info("customer id: {}".format(get_conf(request).get('customer_id', 'unknown')))


@view_config(
    route_name='download_logs',
    permission='maintain',
    request_method='GET'
)
def download_logs(request):
    """
    Return the log archive as the response. Call this method once archiving of
    log files is done.
    """

    return get_file_download_response(
            file_system_path=LOG_ARCHIVE_PATH,
            mime_type='application/zip',
            name_prefix='aerofs-appliance-logs_{}_'.format(get_private_version(request.registry.settings)),
            name_suffix='.zip')


def get_file_download_response(file_system_path, mime_type, name_prefix,
                               name_suffix):
    """
    Return the HTTP Response for the browser to download the file specified in
    the path. The file name is name_prefix + <current time> + name_suffix.

    Also see
    http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/static_assets/files.html
    for alternatives to send file content as responses.
    """
    # The browser will use this name as the name for the downloaded file
    name = get_download_file_name(name_prefix, name_suffix)
    f = open(file_system_path)
    return Response(content_type=mime_type, app_iter=f,
                    content_disposition='attachment; filename={}'.format(name))


def get_download_file_name(name_prefix, name_suffix):
    return '{}{}{}'.format(name_prefix,
                           datetime.today().strftime('%Y%m%d-%H%M%S'),
                           name_suffix)

# HACK ALERT: we don't have a system to persist bunker applications' states,
#   so I end up persisting them in the configuration properties so that they
#   can survive an appliance upgrade.
#
# read from these configuration properties
_DRYAD_OPTION_PROP = 'base.dryad.option'
_DRYAD_HOST_PROP = 'base.dryad.host'
_DRYAD_PORT_PROP = 'base.dryad.port'
_DRYAD_CERT_PROP = 'base.dryad.cert'
_WWW_SUPPORT_PROP = 'base.www.support_email_address'

# write to these external properties
_DRYAD_OPTION_EXTERNAL_PROP = 'dryad_option'
_DRYAD_HOST_EXTERNAL_PROP = 'dryad_host'
_DRYAD_PORT_EXTERNAL_PROP = 'dryad_port'
_DRYAD_CERT_EXTERNAL_PROP = 'dryad_cert'

_FORM_PARAM_DEFECT_ID = 'defect_id'
_FORM_PARAM_USERS = 'users'
_FORM_PARAM_OPTION = 'option'
_FORM_PARAM_EMAIL = 'email'
_FORM_PARAM_HOST = 'host'
_FORM_PARAM_PORT = 'port'
_FORM_PARAM_CERT = 'cert'
_FORM_PARAM_SUBJECT = 'subject'
_FORM_PARAM_MESSAGE = 'message'

_OPTION_AEROFS = 'aerofs'
_OPTION_ON_SITE = 'on-site'
_OPTIONS = [_OPTION_AEROFS, _OPTION_ON_SITE]


@view_config(
    route_name='collect_logs',
    permission='maintain',
    renderer='collect_logs.mako',
    request_method='GET',
)
def collect_logs(request):
    _log_customer_id(request)

    conf = get_conf(request)

    return {
        'defect_id':    request.params.get(_FORM_PARAM_DEFECT_ID,
                                           _generate_defect_id()),
        'users':        request.params.getall(_FORM_PARAM_USERS),
        'option':       conf[_DRYAD_OPTION_PROP],
        'email':        conf[_WWW_SUPPORT_PROP],
        'host':         conf[_DRYAD_HOST_PROP],
        'port':         conf[_DRYAD_PORT_PROP]
        if conf[_DRYAD_PORT_PROP] != '' else '443',
        'cert':         unformat_pem(conf[_DRYAD_CERT_PROP]),
        'subject':      request.params.get(_FORM_PARAM_SUBJECT, ''),
        'message':      request.params.get(_FORM_PARAM_MESSAGE, ''),
    }


@view_config(
    route_name='json_get_users',
    permission='maintain',
    renderer='json',
    request_method='GET',
)
def json_get_users(request):
    url = _get_users_url(request)
    users = requests.get(url).json()

    return {
        'users':    users,
    }


@view_config(
    route_name='json_collect_logs',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def json_collect_logs(request):
    url = _collect_logs_url(request)

    _validate_collect_logs_options(request.params)

    payload = {
        'defectID': request.params.get(_FORM_PARAM_DEFECT_ID,
        # front-end should have prevented this; falls back gracefully anyway
                                       _generate_defect_id()),
        'version':  get_private_version(request.registry.settings),
        'users':    request.params.getall(_FORM_PARAM_USERS),
        'option':   request.params[_FORM_PARAM_OPTION],
        'email':    request.params[_FORM_PARAM_EMAIL],
        'host':     request.params[_FORM_PARAM_HOST],
        'port':     request.params[_FORM_PARAM_PORT],
        'cert':     request.params[_FORM_PARAM_CERT],
        'subject':  request.params[_FORM_PARAM_SUBJECT],
        'message':  request.params[_FORM_PARAM_MESSAGE],
    }

    r = requests.post(url, data=payload)

    # Only persist settings if the post was successful.
    if r.status_code == 200:
        conf = get_conf_client(request)
        conf.set_external_property(_DRYAD_OPTION_EXTERNAL_PROP,
                                   request.params[_FORM_PARAM_OPTION])
        conf.set_external_property(_DRYAD_HOST_EXTERNAL_PROP,
                                   request.params[_FORM_PARAM_HOST])
        conf.set_external_property(_DRYAD_PORT_EXTERNAL_PROP,
                                   request.params[_FORM_PARAM_PORT])
        conf.set_external_property(_DRYAD_CERT_EXTERNAL_PROP,
                                   format_pem(request.params[_FORM_PARAM_CERT]))
    else:
        unexpected_error('AeroFS was unable to issue commands to collect logs. Please '
              'try again later.')


def _log_collection_base_url(request):
    return request.registry.settings["deployment.log_collection_server_uri"]


def _get_users_url(request):
    return _log_collection_base_url(request) + "/users"


def _collect_logs_url(request):
    return _log_collection_base_url(request) + "/collect_logs"


def _generate_defect_id():
    return uuid.uuid4().hex


def _validate_collect_logs_options(params):
    if _FORM_PARAM_OPTION not in params:
        expected_error('Please select a destination to send logs to.')
    elif params[_FORM_PARAM_OPTION] not in _OPTIONS:
        expected_error('Please select a valid destination to send logs to.')

    if params[_FORM_PARAM_OPTION] == _OPTION_AEROFS:
        if _FORM_PARAM_EMAIL not in params:
            expected_error('Please provide a contact e-mail to reach you at.')
            # TODO: validate the email somehow
    elif params[_FORM_PARAM_OPTION] == _OPTION_ON_SITE:
        if _FORM_PARAM_HOST not in params:
            expected_error('Please enter the hostname of your on-site server.')
            # TODO: validate the hostname somehow

        if _FORM_PARAM_PORT not in params:
            expected_error('Please enter the port of your on-site server.')
            # TODO: validate the port number somehow

        if _FORM_PARAM_CERT not in params:
            expected_error('Please provide the certificate of your on-site server.')
        else:
            _validate_certificate(params[_FORM_PARAM_CERT])

    if _FORM_PARAM_SUBJECT not in params:
        expected_error('Please provide a subject.')
    if _FORM_PARAM_MESSAGE not in params:
        expected_error('Please provide a message that describes the problem.')


def _validate_certificate(certificate):
    msg = 'The certificate you have provided is invalid. Please provide a ' \
          'valid X509 SSL certificate file in the PEM format.'

    try:
        filename = write_pem_to_file(certificate)
        try:
            if not is_certificate_formatted_correctly(filename):
                expected_error(msg)
        finally:
            os.unlink(filename)
    except UnicodeEncodeError:
        # this error indicates bad unicode in the cert => bad cert
        expected_error(msg)

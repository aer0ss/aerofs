# Views for site setup. Related document: docs/design/pyramid_auth.md

import logging
import shutil
import os
import socket
import re
import markupsafe
from web.util import str2bool, is_configuration_initialized_in_private_deployment
from pyramid.security import NO_PERMISSION_REQUIRED, remember

import requests
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPOk, HTTPFound
from aerofs_common.bootstrap import BootstrapClient
from web.error import error
from web.license import set_license_file_and_attach_shasum_to_session, is_license_present_and_valid
from backup_view import BACKUP_FILE_PATH, example_backup_download_file_name
from maintenance_util import write_pem_to_file, \
    format_pem, is_certificate_formatted_correctly, \
    get_modulus_of_certificate_file, get_modulus_of_key_file, \
    is_key_formatted_correctly, get_conf_client, get_conf

log = logging.getLogger(__name__)


# Base URL for all calls to the tomcat verification servlet.
def verification_base_url(request):
    return request.registry.settings["deployment.verification_server_uri"]


# This is a tomcat servlet that is part of the SP package.
def _email_verification_url(request):
    return verification_base_url(request) + "/email"

# N.B. these params are also defined in Java land in SmtpVerificationServlet.java
_SMTP_VERIFICATION_FROM_EMAIL = "from_email"
_SMTP_VERIFICATION_TO_EMAIL = "to_email"
_SMTP_VERIFICATION_CODE = "verification_code"
_SMTP_VERIFICATION_SMTP_HOST = "email_sender_public_host"
_SMTP_VERIFICATION_SMTP_PORT = "email_sender_public_port"
_SMTP_VERIFICATION_SMTP_USERNAME = "email_sender_public_username"
_SMTP_VERIFICATION_SMTP_PASSWORD = "email_sender_public_password"
_SMTP_VERIFICATION_SMTP_ENABLE_TLS = "email_sender_public_enable_tls"
_SMTP_VERIFICATION_SMTP_CERT = "email_sender_public_cert"

# ------------------------------------------------------------------------
# Setup View
# ------------------------------------------------------------------------

@view_config(
    route_name='setup',
    permission='maintain',
    renderer='setup/setup.mako',
)
def setup(request):
    conf = get_conf(request)
    page = request.params.get('page')
    page = int(page) if page else 0

    return {
        'page': page,
        'current_config': conf,
        'is_configuration_initialized': is_configuration_initialized_in_private_deployment(),
        'enable_data_collection': _is_data_collection_enabled(conf),
        'restored_from_backup': _is_restored_from_backup(conf),
        # The following parameter is used by license_page.mako
        'is_license_present_and_valid': is_license_present_and_valid(conf),
        # The following parameter is used by create_or_restore.mako
        'example_backup_download_file_name': example_backup_download_file_name(),
        # The following parameter is used by email_page.mako
        'default_support_email': _get_default_support_email(conf['base.host.unified']),
        # The following parameter is used by already_restored_page.mako.
        # TODO (WW) This really smells. Refactor setup.mako.
        'get_already_restored_html_message': _get_already_restored_html_message(request)
    }


def _get_default_support_email(hostname):
    if not hostname or _is_ipv4_address(hostname):
        # Return an empty support email address if the hostname is invalid or
        # it's a IP address. Although we can return 'support@[1.2.3.4]' but this
        # format is rarely used. See the "Domain Part" section in
        # http://en.wikipedia.org/wiki/Email_address.
        return ''
    else:
        # Get the hostname excluding the first level (left-most) subdomain. e.g.
        # given "share.google.com" return "google.com". See the test code for
        # expected behavior.
        match = re.search(r'^[^\.]+\.(.+)', hostname)
        domain = match.group(1) if match else hostname
        return 'support@{}'.format(domain)


def _is_ipv4_address(string):
    import socket
    try:
        socket.inet_aton(string)
        return True
    except socket.error:
        return False


# ------------------------------------------------------------------------
# License
# ------------------------------------------------------------------------

@view_config(
    route_name='json_set_license',
    # This method doesn't require authentication.
    # See docs/design/pyramid_auth.md.
    permission=NO_PERMISSION_REQUIRED,
    renderer='json',
    request_method='POST'
)
def json_set_license(request):
    log.info("set license")

    # TODO (WW) share code with maintenance_view.py:login_submit()?

    # Due to the way we use JS to upload this file, the request parameter on
    # the wire is urlencoded utf8 of a unicode string.
    # request.params['license'] is that unicode string.
    # We want raw bytes, not the Unicode string, so we encode to latin1
    license_bytes = request.params['license'].encode('latin1')

    if not set_license_file_and_attach_shasum_to_session(request, license_bytes):
        error("The provided license file is invalid.")

    headers = remember(request, 'fakeuser')
    return HTTPOk(headers=headers)


# ------------------------------------------------------------------------
# Data collection
# ------------------------------------------------------------------------

@view_config(
    route_name='setup_submit_data_collection_form',
    permission='maintain',
    request_method='POST'
)
def setup_submit_data_collection_form(request):
    enable = request.params['data-collection']
    _set_data_collection(request, enable)
    return HTTPFound(location=_get_hostname_page_route_path(request))


@view_config(
    route_name='json_setup_disable_data_collection',
    permission='permission',
    renderer='json',
    request_method='POST'
)
def json_setup_disable_data_collection(request):
    _set_data_collection(request, "false")


def _set_data_collection(request, enable):
    """
    @param enable string "true" or "false" to enable or disable data collection
    """
    log.info("appliance setup data collection: {}".format(enable))
    config = get_conf_client(request)
    config.set_external_property('enable_appliance_setup_data_collection', enable)


def _is_data_collection_enabled(conf):
    enabled = conf['web.enable_appliance_setup_data_collection']
    # To be safe for the customers, use False as the default
    enabled = False if not enabled else str2bool(enabled)

    if enabled:
        # enable only for trial licenses. By default don't enable it, which is
        # required for older licenses without the trial flag.
        enabled = str2bool(conf.get('license_is_trial', False))

    return enabled


def _get_hostname_page_route_path(request):
    # TODO (WW) fix this hack and have dedicated route for each setup page.
    return request.route_path('setup', _query={'page': '1'})

# ------------------------------------------------------------------------
# Hostname
# ------------------------------------------------------------------------

@view_config(
    route_name='json_setup_hostname',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_hostname(request):
    hostname = request.params['base.host.unified']

    # disallow the IP range 127/8
    local_ips = re.compile("^127.\d{1,3}.\d{1,3}.\d{1,3}$")

    if hostname == "localhost" or local_ips.match(hostname):
        error("Local hostnames or IP addresses are not allowed.")
    elif not _is_hostname_resolvable(hostname):
        error("Unable to resolve " + hostname + ". Please check your settings.")

    conf_client = get_conf_client(request)
    conf_client.set_external_property('base_host', hostname)

    return {}


def _is_hostname_resolvable(hostname):
    try:
        socket.gethostbyname(hostname)
        return True
    except socket.error:
        return False


# ------------------------------------------------------------------------
# Email
# ------------------------------------------------------------------------

def _parse_email_request(request):
    if request.params['email-server'] == 'remote':
        host = request.params['email-sender-public-host']
        port = request.params['email-sender-public-port']
        username = request.params['email-sender-public-username']
        password = request.params['email-sender-public-password']
        # N.B. if a checkbox is unchecked, its value is not included in the
        # request. This is the way that JavaScript's .serialize() works,
        # apparently. So, we use the presence of the param as its value.
        enable_tls = 'email-sender-public-enable-tls' in request.params
        smtp_cert = request.params['email-sender-public-cert']
    else:
        host = 'localhost'
        port = '25'
        username = ''
        password = ''
        # The server ignores this flag if mail relay is localhost.
        enable_tls = False
        smtp_cert = ''

    support_address = request.params['base-www-support-email-address']
    return host, port, username, password, enable_tls, smtp_cert, support_address


def _send_verification_email(url, from_email, to_email, code, host, port,
                             username, password, enable_tls, smtp_cert):
    payload = {
        _SMTP_VERIFICATION_FROM_EMAIL: from_email,
        _SMTP_VERIFICATION_TO_EMAIL: to_email,
        _SMTP_VERIFICATION_CODE: code,
        _SMTP_VERIFICATION_SMTP_HOST: host,
        _SMTP_VERIFICATION_SMTP_PORT: port,
        _SMTP_VERIFICATION_SMTP_USERNAME: username,
        _SMTP_VERIFICATION_SMTP_PASSWORD: password,
        _SMTP_VERIFICATION_SMTP_ENABLE_TLS: enable_tls,
        _SMTP_VERIFICATION_SMTP_CERT: smtp_cert
    }

    return requests.post(url, data=payload)


@view_config(
    route_name='json_verify_smtp',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_verify_smtp(request):
    host, port, username, password, enable_tls, smtp_cert, support_address = \
        _parse_email_request(request)

    verify_code = request.params['verification-code']
    verify_email = request.params['verification-to-email']

    # Save the email for the frontend to use next time
    conf_client = get_conf_client(request)
    conf_client.set_external_property('last_smtp_verification_email', verify_email)

    url = _email_verification_url(request)
    r = _send_verification_email(url, support_address, verify_email,
                                 verify_code, host, port,
                                 username, password, enable_tls, smtp_cert)

    if r.status_code != 200:
        log.error("send stmp verification email returns {}".format(r.status_code))

        if r.status_code == 400:
            # In this case we have a human readable error. Hopefully it will help them
            # debug their smtp issues. Return the error string.
            # We don't want to show stack dumps for internal failures (500 or any
            # other unexpected failure...)
            error("Unable to send email. The error is:<br>" + markupsafe.escape(r.text))
        else:
            error("Unable to send email. Please check your SMTP settings.")

    return {}


@view_config(
    route_name='json_setup_email',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_email(request):
    host, port, username, password, enable_tls, smtp_cert, support_address = \
        _parse_email_request(request)

    configuration = get_conf_client(request)
    configuration.set_external_property('support_address',   support_address)
    configuration.set_external_property('email_host',        host)
    configuration.set_external_property('email_port',        port)
    configuration.set_external_property('email_user',        username)
    configuration.set_external_property('email_password',    password)
    configuration.set_external_property('email_enable_tls',  enable_tls)
    configuration.set_external_property('email_cert',        format_pem(smtp_cert))

    return {}


# ------------------------------------------------------------------------
# Certificate
# ------------------------------------------------------------------------

@view_config(
    route_name='json_setup_certificate',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_certificate(request):
    certificate = request.params['server.browser.certificate']
    key = request.params['server.browser.key']

    certificate_filename = write_pem_to_file(certificate)
    key_filename = write_pem_to_file(key)

    try:
        is_certificate_valid = is_certificate_formatted_correctly(certificate_filename)
        is_key_valid = is_key_formatted_correctly(key_filename)

        if not is_certificate_valid and not is_key_valid:
            error("The certificate and key you provided is invalid.")
        elif not is_certificate_valid:
            error("The certificate you provided is invalid.")
        elif not is_key_valid:
            error("The key you provided is invalid.")

        # Check that key matches the certificate.
        certificate_modulus = get_modulus_of_certificate_file(certificate_filename)
        key_modulus = get_modulus_of_key_file(key_filename)

        if certificate_modulus != key_modulus:
            error("The certificate and key you provided do not match each other.")

        # All is well - set the external properties.
        configuration = get_conf_client(request)
        configuration.set_external_property('browser_cert', format_pem(certificate))
        configuration.set_external_property('browser_key', format_pem(key))

        return {}

    finally:
        os.unlink(certificate_filename)
        os.unlink(key_filename)


# ------------------------------------------------------------------------
# Restore from backup data
# ------------------------------------------------------------------------

@view_config(
    route_name='json_upload_backup',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_upload_backup(request):
    # We don't support restoring multiple time since restoration is not an idempotent operation.
    if _is_restored_from_backup(get_conf(request)):
        error(_get_already_restored_html_message(request))

    log.info("uploading backup file...")
    # Clean up old file
    if os.path.exists(BACKUP_FILE_PATH):
        os.remove(BACKUP_FILE_PATH)

    # See http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/forms/file_uploads.html
    input_file = request.POST['backup-file'].file
    input_file.seek(0)

    with open(BACKUP_FILE_PATH, 'wb') as output_file:
        shutil.copyfileobj(input_file, output_file)

    return HTTPOk()


@view_config(
    route_name='json_setup_set_restored_from_backup',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_set_restored_from_backup(request):
    """
    Call this method after restoration is completely successful
    """
    conf = get_conf_client(request)
    conf.set_external_property('restored_from_backup', 'true')


def _is_restored_from_backup(conf):
    return str2bool(conf['restored_from_backup'])


def _get_already_restored_html_message(request):
    return 'This appliance has already been restored from a backup file. Please' \
           ' <a href="{}">click here</a> to finish the setup, or discard this appliance' \
           ' and launch a new one to start over.'.format(
           _get_hostname_page_route_path(request))


# ------------------------------------------------------------------------
# Finalize
# ------------------------------------------------------------------------

@view_config(
    route_name='json_setup_finalize',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_finalize(request):
    log.warn("finalizing configuration...")
    bootstrap_client = BootstrapClient(request.registry.settings["deployment.bootstrap_server_uri"])
    bootstrap_client.enqueue_task_set("set-configuration-initialized")
    return {}

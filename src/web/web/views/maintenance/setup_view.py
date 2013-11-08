# Views for site setup. Related document: docs/design/site_setup_auth.md
#

import logging
import shutil
import tempfile
import os
import socket
import random
import re
from subprocess import call, Popen, PIPE
from pyramid.security import NO_PERMISSION_REQUIRED, remember

import requests
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPFound, HTTPOk, HTTPInternalServerError, HTTPBadRequest

import aerofs_common.bootstrap
from aerofs_common.configuration import Configuration
from web.auth import NON_SP_USER_ID, is_authenticated
from web.error import error
from web.util import is_private_deployment, is_configuration_initialized
from web.license import is_license_present_and_valid, is_license_present, set_license_file_and_shasum
from backup_view import BACKUP_FILE_PATH
from web.views.login.login_view import URL_PARAM_EMAIL

log = logging.getLogger("web")

# ------------------------------------------------------------------------
# Verification interface constants.
# ------------------------------------------------------------------------

# Base URL for all calls to the tomcat verification servlet.
_VERIFICATION_BASE_URL = "http://localhost:8080/verify/"

# This is a tomcat servlet that is part of the SP package.
_SMTP_VERIFICATION_URL = _VERIFICATION_BASE_URL + "email"
# N.B. these params are also defined in Java land in SmtpVerificationServlet.java
_SMTP_VERIFICATION_FROM_EMAIL = "from_email"
_SMTP_VERIFICATION_TO_EMAIL = "to_email"
_SMTP_VERIFICATION_CODE = "verification_code"
_SMTP_VERIFICATION_SMTP_HOST = "email_sender_public_host"
_SMTP_VERIFICATION_SMTP_PORT = "email_sender_public_port"
_SMTP_VERIFICATION_SMTP_USERNAME = "email_sender_public_username"
_SMTP_VERIFICATION_SMTP_PASSWORD = "email_sender_public_password"
_SMTP_VERIFICATION_SMTP_ENABLE_TLS = "email_sender_public_enable_tls"

# LDAP verification servlet URL.
_LDAP_VERIFICATION_URL = _VERIFICATION_BASE_URL + "ldap"

# ------------------------------------------------------------------------
# Session keys
# ------------------------------------------------------------------------

_SESSION_KEY_EMAIL_VERIFICATION_CODE = 'email_verification_code'

# ------------------------------------------------------------------------
# Settings utilities
# ------------------------------------------------------------------------

def _get_configuration():
    properties = {}
    configuration = Configuration()
    configuration.fetch_and_populate(properties)

    return properties

# ------------------------------------------------------------------------
# Setup View
# ------------------------------------------------------------------------

@view_config(
    route_name='setup',
    # Should not require permission. See docs/design/site_setup_auth.md.
    permission=NO_PERMISSION_REQUIRED,
    renderer='setup.mako'
)
def setup(request):
    conf = _get_configuration()
    # See docs/design/site_setup_auth.md for explanation of the following logic.
    if is_license_present_and_valid(conf):
        log.info("license is valid. redirect to setup_authorized")
        return HTTPFound(request.route_path("setup_authorized", _query=request.params))
    else:
        log.info("license is invalid. ask for license")
        return _setup_common(request, conf, True)

@view_config(
    route_name='setup_authorized',
    permission='maintain',
    renderer='setup.mako',
)
def setup_authorized(request):
    return _setup_common(request, _get_configuration(), False)

def _setup_common(request, conf, license_page_only):
    # Site setup is available only in private deployment
    if not is_private_deployment(conf):
        raise HTTPBadRequest("the page is not available")

    # Genearate email verification code. Keep the code constant across the
    # session so if the user sends multiple verification emails the user can use
    # the code from any email.
    code = request.session.get(_SESSION_KEY_EMAIL_VERIFICATION_CODE)
    if not code:
        code = random.randint(100000, 999999)
        request.session[_SESSION_KEY_EMAIL_VERIFICATION_CODE] = code

    if license_page_only:
        # We assume page 0 is the license page.
        page = 0
    else:
        page = request.params.get('page')
        page = int(page) if page else 0

    return {
        'page': page,
        'current_config': conf,
        'is_configuration_initialized': is_configuration_initialized(conf),
        # The following two parameters are used by welcome_and_license.mako
        'is_license_present': is_license_present(conf),
        'is_license_present_and_valid': is_license_present_and_valid(conf),
        # This parameter is used by apply_and_create_user_page.mako
        'url_param_email': URL_PARAM_EMAIL,
        # The following two parameters are used by email_page.mako
        'email_verification_code': code,
        'default_support_email': _get_default_support_email(conf['base.host.unified'])
    }

def _get_default_support_email(hostname):
    if not hostname: hostname = 'localhost'

    # Get the hostname excluding the first level (left-most) subdomain. e.g.
    # given "share.google.com" return "google.com". See the test code for the
    # exact spec.
    match = re.search(r'^[^\.]+\.(.+)', hostname)
    return 'support@{}'.format(match.group(1) if match else hostname)

# ------------------------------------------------------------------------
# License
# ------------------------------------------------------------------------

@view_config(
    route_name='json_set_license',
    # This method doesn't require authentication.
    # See docs/design/site_setup_auth.md.
    permission=NO_PERMISSION_REQUIRED,
    renderer='json',
    request_method='POST'
)
def json_set_license(request):
    log.info("set license")
    # Due to the way we use JS to upload this file, the request parameter on
    # the wire is urlencoded utf8 of a unicode string.
    # request.params['license'] is that unicode string.
    # We want raw bytes, not the Unicode string, so we encode to latin1
    set_license_file_and_shasum(request, request.params['license'].encode('latin1'))

    # Log in the user as a maintainer if the user hasn't logged in yet. The
    # test is necessary to keep the real user ID if the user is already
    # logged in with the real ID.
    if not is_authenticated(request):
        remember(request, NON_SP_USER_ID)

    return HTTPOk()

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

    if hostname == "localhost":
        error("Localhost is not an acceptable name. Please configure your DNS.")
    elif _is_valid_ipv4_address(hostname):
        # We can't use IP addresses as hostnames, because the Java security
        # library can't verify certificates with IP addresses as their CNames.
        error("IP addresses are not allowed. Please configure your DNS.")
    elif not _is_hostname_resolvable(hostname):
        error("Unable to resolve " + hostname + ". Please check your settings.")

    Configuration().set_external_property('base_host', hostname)

    return {}

def _is_valid_ipv4_address(string):
    import socket
    try:
        socket.inet_aton(string)
        return True
    except socket.error:
        return False

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
        host       = request.params['email-sender-public-host']
        port       = request.params['email-sender-public-port']
        username   = request.params['email-sender-public-username']
        password   = request.params['email-sender-public-password']
        # N.B. if a checkbox is unchecked, its value is not included in the request. This is
        # the way that javascript's .serialize() works, apparently. So, we use the presence
        # of the param as its value.
        enable_tls = 'email-sender-public-enable-tls' in request.params
    else:
        host = 'localhost'
        port = '25'
        username = ''
        password = ''
        enable_tls = True

    support_address = request.params['base-www-support-email-address']
    return host, port, username, password, enable_tls, support_address

def _send_verification_email(from_email, to_email, code, host, port,
                             username, password, enable_tls):
    payload = {
        _SMTP_VERIFICATION_FROM_EMAIL: from_email,
        _SMTP_VERIFICATION_TO_EMAIL: to_email,
        _SMTP_VERIFICATION_CODE: code,
        _SMTP_VERIFICATION_SMTP_HOST: host,
        _SMTP_VERIFICATION_SMTP_PORT: port,
        _SMTP_VERIFICATION_SMTP_USERNAME: username,
        _SMTP_VERIFICATION_SMTP_PASSWORD: password,
        _SMTP_VERIFICATION_SMTP_ENABLE_TLS: enable_tls
    }

    return requests.post(_SMTP_VERIFICATION_URL, data=payload)

@view_config(
    route_name='json_verify_smtp',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_verify_smtp(request):
    host, port, username, password, enable_tls, support_address = _parse_email_request(request)

    r = _send_verification_email(
            support_address,
            request.params['verification-to-email'],
            request.session[_SESSION_KEY_EMAIL_VERIFICATION_CODE],
            host,
            port,
            username,
            password,
            enable_tls)

    if r.status_code != 200:
        log.error("send stmp verification email returns {}".format(r.status_code))
        error("Unable to send email. Please check your SMTP settings.")

    return {}

@view_config(
    route_name='json_setup_email',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_setup_email(request):
    host, port, username, password, enable_tls, support_address = _parse_email_request(request)

    configuration = Configuration()
    configuration.set_external_property('support_address',   support_address)
    configuration.set_external_property('email_host',        host)
    configuration.set_external_property('email_port',        port)
    configuration.set_external_property('email_user',        username)
    configuration.set_external_property('email_password',    password)
    configuration.set_external_property('email_enable_tls',  enable_tls)

    return {}

# ------------------------------------------------------------------------
# Certificate
# ------------------------------------------------------------------------

def _is_certificate_formatted_correctly(certificate_filename):
    return call(["/usr/bin/openssl", "x509", "-in", certificate_filename, "-noout"]) == 0

def _is_key_formatted_correctly(key_filename):
    return call(["/usr/bin/openssl", "rsa", "-in", key_filename, "-noout"]) == 0

def _write_pem_to_file(pem_string):
    os_handle, filename = tempfile.mkstemp()
    os.write(os_handle, pem_string)
    os.close(os_handle)
    return filename

def _get_modulus_helper(cmd):
    p = Popen(cmd, stdout=PIPE, stderr=PIPE)
    stdout, stderr = p.communicate()
    return stdout.split('=')[1]

# Expects as input a filename pointing to a valid PEM certtificate file.
def _get_modulus_of_certificate_file(certificate_filename):
    cmd = ["/usr/bin/openssl", "x509", "-noout", "-modulus", "-in", certificate_filename]
    return _get_modulus_helper(cmd)

# Expects as input a filename pointing to a valid PEM key file.
def _get_modulus_of_key_file(key_filename):
    cmd = ["/usr/bin/openssl", "rsa", "-noout", "-modulus", "-in", key_filename]
    return _get_modulus_helper(cmd)

# Format certificate and key using this function before saving to the
# configuration service.
# See also the code in identity_page.mako that convert the string to HTML format.
def _format_pem(string):
        return string.strip().replace('\n', '\\n').replace('\r', '')

@view_config(
    route_name = 'json_setup_certificate',
    permission='maintain',
    renderer = 'json',
    request_method = 'POST'
)
def json_setup_certificate(request):
    certificate = request.params['server.browser.certificate']
    key = request.params['server.browser.key']

    certificate_filename = _write_pem_to_file(certificate)
    key_filename = _write_pem_to_file(key)

    try:
        is_certificate_valid = _is_certificate_formatted_correctly(certificate_filename)
        is_key_valid = _is_key_formatted_correctly(key_filename)

        if not is_certificate_valid and not is_key_valid:
            error("The certificate and key you provided is invalid.")
        elif not is_certificate_valid:
            error("The certificate you provided is invalid.")
        elif not is_key_valid:
            error("The key you provided is invalid.")

        # Check that key matches the certificate.
        certificate_modulus = _get_modulus_of_certificate_file(certificate_filename)
        key_modulus = _get_modulus_of_key_file(key_filename)

        if certificate_modulus != key_modulus:
            error("The certificate and key you provided do not match each other.")

        # All is well - set the external properties.
        configuration = Configuration()
        configuration.set_external_property('browser_cert', _format_pem(certificate))
        configuration.set_external_property('browser_key', _format_pem(key))

        return {}

    finally:
        os.unlink(certificate_filename)
        os.unlink(key_filename)

# ------------------------------------------------------------------------
# Identity
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_verify_ldap',
    permission='maintain',
    renderer = 'json',
    request_method = 'POST'
)
def json_verify_ldap(request):
    cert = request.params['ldap_server_ca_certificate']
    if cert and not _is_certificate_formatted_correctly(_write_pem_to_file(cert)):
        error("The certificate you provided is invalid. "
              "Please provide one in PEM format.")

    payload = {}
    for key in _get_ldap_specific_parameters(request.params):
        # N.B. need to convert to ascii. The request params given to us in
        # unicode format.
        payload[key] = request.params[key].encode('ascii', 'ignore')

    r = requests.post(_LDAP_VERIFICATION_URL, data=payload)

    if r.status_code == 200:
        return {}

    # In this case we have a human readable error. Hopefully it will help them
    # debug their LDAP issues. Return the error string.
    if r.status_code == 400:
        error("We couldn't connect to the LDAP server. Please check your "
              "settings. The error is:<br>" + r.text)

    # Server failure. No human readable error message is available.
    raise HTTPInternalServerError()

@view_config(
    route_name = 'json_setup_identity',
    permission='maintain',
    renderer = 'json',
    request_method = 'POST'
)
def json_setup_identity(request):
    log.info("setup identity")

    auth = request.params['authenticator']
    ldap = auth == 'external_credential'

    # All is well - set the external properties.
    conf = Configuration()
    conf.set_external_property('authenticator', auth)
    if ldap: _write_ldap_properties(conf, request.params)

    return HTTPOk()

def _write_ldap_properties(conf, request_params):
    for key in _get_ldap_specific_parameters(request_params):
        if key == 'ldap_server_ca_certificate':
            cert = request_params[key]
            if cert: conf.set_external_property(key, _format_pem(cert))
            else: conf.set_external_property(key, '')
        else:
            conf.set_external_property(key, request_params[key])

def _get_ldap_specific_parameters(request_params):
    """
    N.B. This method assumes that an HTTP parameter is LDAP specific iff. it has
    the "ldap_" prefix.
    """
    ldap_params = []
    for key in request_params:
        if key[:5] == 'ldap_':
            ldap_params.append(key)

    return ldap_params

# ------------------------------------------------------------------------
# Restore from backup data
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_upload_backup',
    permission='maintain',
    renderer = 'json',
    request_method = 'POST'
)
def json_restore(request):
    log.info("uploading backup file...")
    # Clean up old file
    if os.path.exists(BACKUP_FILE_PATH): os.remove(BACKUP_FILE_PATH)

    # See http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/forms/file_uploads.html
    input_file = request.POST['backup-file'].file
    input_file.seek(0)

    with open(BACKUP_FILE_PATH, 'wb') as output_file:
        shutil.copyfileobj(input_file, output_file)

    return HTTPOk()

# ------------------------------------------------------------------------
# Finalize
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_setup_finalize',
    permission='maintain',
    renderer = 'json',
    request_method = 'POST'
)
def json_setup_finalize(request):
    log.warn("finalizing configuration...")

    configuration = Configuration()
    configuration.set_external_property('configuration_initialized', 'true')

    # Finally, ask ourselves to load new configuration values. Doing so in
    # json_setup_apply() (i.e. placing uwsgi-reload in manual.tasks) would be
    # ideal. However, we can't because:
    #
    # o During initial setup the Web session is not authenticated (thanks to
    #   the redirect middleware).
    # o Once configuration_initialized is set to true, we (the Python server)
    #   will require an admin credential to access json_setup_*().
    # o JavaScript calls json_setup_apply/poll/finalize in a single HTML page
    #   in that order to perform the setup process.
    # o As a result, if the reload is done in json_setup_apply(), the JS during
    #   initial setup wouldn't be able to call json_setup_poll or finalize after
    #   calling apply.
    #
    aerofs_common.bootstrap.enqueue_task_set("web-reload")
    return {}

import logging
import tempfile
import shutil
import time
import os
import socket
from aerofs_common.configuration import Configuration
from pyramid.view import view_config
from subprocess import call, Popen, PIPE
from pyramid.httpexceptions import HTTPFound
from web.util import *

BOOTSTRAP_PIPE_FILE = "/tmp/bootstrap"

log = logging.getLogger("web")

def get_settings():
    settings = {}
    configuration = Configuration()
    configuration.fetch_and_populate(settings)

    return settings

@view_config(
    route_name='setup',
    permission='admin',
    renderer='setup.mako'
)
def setup_view(request):
    settings = get_settings()

    return {
        'current_config': settings
    }

# ------------------------------------------------------------------------
# Hostname
# ------------------------------------------------------------------------

def hostname_resolves(hostname):
    try:
        socket.gethostbyname(hostname)
        return True
    except socket.error:
        return False

@view_config(
    route_name = 'json_setup_hostname',
    permission='admin',
    renderer = 'json'
)
def json_setup_hostname(request):
    base_host_unified = request.params['base.host.unified']

    if not hostname_resolves(base_host_unified):
        error('Unable to resolve ' + base_host_unified + '. Please check your settings.')

    if base_host_unified == "localhost":
        error("Localhost is not an acceptable name. Please configure your DNS.")

    configuration = Configuration()
    configuration.set_persistent_value('base_host', base_host_unified)

    return {}

# ------------------------------------------------------------------------
# Email
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_setup_email',
    permission='admin',
    renderer = 'json'
)
def json_setup_email(request):
    base_www_support_email_address = request.params['base.www.support_email_address']

    if request.params['email.server'] == 'remote':
        email_sender_public_host       = request.params['email.sender.public_host']
        email_sender_public_username   = request.params['email.sender.public_username']
        email_sender_public_password   = request.params['email.sender.public_password']
    else:
        email_sender_public_host = ''
        email_sender_public_username = ''
        email_sender_public_password = ''

    configuration = Configuration()

    # TODO (MP) need better sanity checking here. Need to make sure SMTP creds will work.

    configuration.set_persistent_value('support_address',   base_www_support_email_address)
    configuration.set_persistent_value('email_host',        email_sender_public_host)
    configuration.set_persistent_value('email_user',        email_sender_public_username)
    configuration.set_persistent_value('email_password',    email_sender_public_password)

    return {}

# ------------------------------------------------------------------------
# Certificate
# ------------------------------------------------------------------------

def is_certificate_formatted_correctly(certificate_filename):
    return call(["/usr/bin/openssl", "x509", "-in", certificate_filename, "-noout"]) == 0

def is_key_formatted_correctly(key_filename):
    return call(["/usr/bin/openssl", "rsa", "-in", key_filename, "-noout"]) == 0

def write_pem_to_file(pem_string):
     os_handle, filename = tempfile.mkstemp()
     os.write(os_handle, pem_string)
     os.close(os_handle)
     return filename

def _get_modulus_helper(cmd):
    p = Popen(cmd, stdout=PIPE, stderr=PIPE)
    stdout, stderr = p.communicate()
    return stdout.split('=')[1]

# Expects as input a filename pointing to a valid PEM certtificate file.
def get_modulus_of_certificate_file(certificate_filename):
    cmd = ["/usr/bin/openssl", "x509", "-noout", "-modulus", "-in", certificate_filename]
    return _get_modulus_helper(cmd)

# Expects as input a filename pointing to a valid PEM key file.
def get_modulus_of_key_file(key_filename):
    cmd = ["/usr/bin/openssl", "rsa", "-noout", "-modulus", "-in", key_filename]
    return _get_modulus_helper(cmd)

# Format certificate and key using this function before saving to the
# configuration service.
def format_pem(string):
        return string.strip().replace('\n', '\\n');

@view_config(
    route_name = 'json_setup_certificate',
    permission='admin',
    renderer = 'json'
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
            error("The certificate and key you provided do not match.")

        # All is well - set the external properties.
        configuration = Configuration()
        configuration.set_persistent_value('browser_cert', format_pem(certificate))
        configuration.set_persistent_value('browser_key', format_pem(key))

        return {}

    finally:
        os.unlink(certificate_filename)
        os.unlink(key_filename)

# ------------------------------------------------------------------------
# Apply
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_setup_apply',
    permission='admin',
    renderer = 'json'
)
def json_setup_apply(request):
    log.warn("Applying configuration.")
    shutil.copyfile('/opt/bootstrap/tasks/manual.tasks', BOOTSTRAP_PIPE_FILE)

    return {}

# ------------------------------------------------------------------------
# Poll
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_setup_poll',
    permission='admin',
    renderer = 'json'
)
def json_setup_poll(request):
    running = os.stat(BOOTSTRAP_PIPE_FILE).st_size != 0
    log.warn("Poll, running:" + str(running))

    return {'completed': not running}

# ------------------------------------------------------------------------
# Finalize
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_setup_finalize',
    permission='admin',
    renderer = 'json'
)
def json_setup_finalize(request):
    log.warn("Finalizing configuration...")

    configuration = Configuration()
    configuration.set_persistent_value('configuration_initialized', 'true')

    with open(BOOTSTRAP_PIPE_FILE, 'w') as f:
        # Add the delay so that we have time to return this call before we reload.
        f.write('delay\nuwsgi-reload')

    return {}

# ------------------------------------------------------------------------
# Redirect
# ------------------------------------------------------------------------

@view_config(
    route_name = 'setup_redirect',
    permission='admin'
)
def setup_redirect(request):
    return HTTPFound(location='/setup')

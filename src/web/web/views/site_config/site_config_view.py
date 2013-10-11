import logging
import re
import shutil
import time
import os
import socket
from aerofs_common.configuration import Configuration
from pyramid.view import view_config
from subprocess import check_call
from subprocess import CalledProcessError
from pyramid.httpexceptions import HTTPFound
from web.util import *

BOOTSTRAP_PIPE_FILE = "/tmp/bootstrap"

log = logging.getLogger("web")

@view_config(
    route_name='site_config',
    permission='admin',
    renderer='site_config.mako'
)
def site_config_view(request):

    settings = {}
    configuration = Configuration()
    configuration.fetch_and_populate(settings)

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
    route_name = 'json_config_hostname',
    permission='admin',
    renderer = 'json'
)
def json_config_hostname(request):
    base_host_unified = request.params['base.host.unified']

    if not hostname_resolves(base_host_unified):
        return {'error': 'Unable to resolve ' + base_host_unified + '. Please check your settings.'}

    if base_host_unified == "localhost":
        return {'error': "Localhost is not an acceptable name."}

    configuration = Configuration()
    configuration.set_persistent_value('base_host', base_host_unified)

    return {}

# ------------------------------------------------------------------------
# Email
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_config_email',
    permission='admin',
    renderer = 'json'
)
def json_config_email(request):
    base_www_support_email_address = request.params['base.www.support_email_address']
    email_sender_public_host       = request.params['email.sender.public_host']
    email_sender_public_username   = request.params['email.sender.public_username']
    email_sender_public_password   = request.params['email.sender.public_password']

    configuration = Configuration()

    # TODO (MP) need better sanity checking here. Need to make sure SMTP creds will work.

    configuration.set_persistent_value('support_address', base_www_support_email_address)
    configuration.set_persistent_value('email_host',       email_sender_public_host)
    configuration.set_persistent_value('email_user',   email_sender_public_username)
    configuration.set_persistent_value('email_password',   email_sender_public_password)

    return {}

# ------------------------------------------------------------------------
# Certificate
# ------------------------------------------------------------------------

def format_pem(string):
    return string.strip().replace('\n', '\\n');

@view_config(
    route_name = 'json_config_certificate',
    permission='admin',
    renderer = 'json'
)
def json_config_certificate(request):
    certificate = format_pem(request.params['server.browser.certificate'])
    key = format_pem(request.params['server.browser.key'])

    # TODO (MP) need better sanity checking on the values of the cert & key (need to make sure nginx can parse these).

    configuration = Configuration()

    configuration.set_persistent_value('browser_cert', certificate)
    configuration.set_persistent_value('browser_key', key)

    return {}

# ------------------------------------------------------------------------
# Apply
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_config_apply',
    permission='admin',
    renderer = 'json'
)
def json_config_apply(request):
    log.warn("Applying configuration.")
    shutil.copyfile('/opt/bootstrap/tasks/manual.tasks', BOOTSTRAP_PIPE_FILE)

    return {}

# ------------------------------------------------------------------------
# Poll
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_config_poll',
    permission='admin',
    renderer = 'json'
)
def json_config_poll(request):
    running = os.stat(BOOTSTRAP_PIPE_FILE).st_size != 0
    log.warn("Poll, running:" + str(running))

    return {'completed': not running}

# ------------------------------------------------------------------------
# Finalize
# ------------------------------------------------------------------------

@view_config(
    route_name = 'json_config_finalize',
    permission='admin',
    renderer = 'json'
)
def json_config_finalize(request):
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
    route_name = 'site_config_redirect',
    permission='admin'
)
def site_config_redirect(request):
    return HTTPFound(location='/site_config')

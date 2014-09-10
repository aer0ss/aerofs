"""
Helper functions for AeroFS website
"""
import re
import logging
import smtplib
from email.mime.text import MIMEText

from error import error
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub

log = logging.getLogger(__name__)


def show_welcome_image_and_set_cookie(request):
    """Checks if the user has seen the splash welcome image before.
    If they haven't, returns true, and sets the cookie so the user
    won't see it next time. Else, returns false."""
    if 'aerofs_welcome_seen' not in request.session and 'aerofs_welcome_seen' not in request.cookies:
        request.response.set_cookie('aerofs_welcome_seen','true')
        return True
    else:
        return False

# Form validation functions

# TODO (WW) move email checking to SP, or at least use error_on_invalid_email()
def is_valid_email(email):
    """
    Performs a very basic validation of the given userid. Returns true if the
    userid passes, false otherwise
    """
    return re.match(r"[^@'\";]+@[^@'\";]+\.[^@'\";]+", email)

# TODO (WW) move email checking to SP
def error_on_invalid_email(email):
    if not is_valid_email(email):
        error("This email doesn't seem to be valid.")

def domain_sanity_check(domain):
    """
    Performs a basic sanity check on a given domain name to verify that it could
    be valid. Returns true if the domain passes, false otherwise
    """
    return len(domain) > 3 and domain.find(' ') < 0 <= domain.find('.')

def _is_ascii(string):
    """
    Checks if a given string is ascii (used by is_valid_password)
    """

    # http://stackoverflow.com/a/196391 (plus first comment) explains how to
    # detect non-ascii strings.
    try:
        string.encode("ascii")
    except UnicodeEncodeError:
        return False
    else:
        return True

def is_valid_password(request, password):
    """
    Returns a tuple of the format (password_is_valid, error_msg) where
    password_is_valid is a boolean set True if the password is valid or False
    otherwise and error_msg is a user-reportable error message if the password
    is not valid (only set when password_is_valid is false).
    """
    _ = request.translate

    if len(password) < 6:
        return False, _("Passwords must have at least 6 characters.")
    elif not _is_ascii(password):
        return False, _("The password contains invalid (non-ASCII) characters.")
    else:
        return True, ""

def get_error(exception):
    """
    DEPRECATED: use error.sp_exception2error instead

    Returns an error message for the given protobuf exception. Expects the
    exception to be of type 'ExceptionReply'.
    """
    return str(exception.reply.message_deprecated)

def parse_rpc_error_exception(request, e):
    """
    DEPRECATED: use error.sp_exception2error instead

    Reads an exception of any sort generated when making an RPC call
    and returns an appropriate error message for that exception.
    """
    _ = request.translate
    if type(e) == ExceptionReply: # RPC Error
        log.error("RPC Error: " + get_error(e))
        return _("${error}", {'error': get_error(e)})
    else: # Internal server error
        log.error("Internal server error: " + str(e))
        return _("Internal server error: ${error}", {'error': str(e)})

# SP RPC helpers
def get_rpc_stub(request):
    """
    Returns an SPServiceRPCStub suitable for making RPC calls. Expects
    request.session['sp_cookies'] to be set (should be by login)
    """
    settings = request.registry.settings
    if 'sp_cookies' in request.session:
        con = SyncConnectionService(settings['base.sp.url'],
                                    settings['sp.version'],
                                    request.session['sp_cookies'])
    else:
        # attempt session recovery
        con = SyncConnectionService(settings['base.sp.url'],
                                    settings['sp.version'])
    return SPServiceRpcStub(con)

def flash_error(request, error_msg):
    """
    Adds the given error message to the error message queue. Call
    get_last_flash_message_and_empty_queue() to retrieve the message.
    """
    request.session.flash(error_msg, 'error_queue')

def flash_success(request, success_msg):
    """
    Adds the given message to the success message queue, Call
    get_last_flash_message_and_empty_queue() to retrieve the message.
    """
    request.session.flash(success_msg, 'success_queue')

def get_last_flash_message_and_empty_queue(request):
    """
    Return None if there is no flash message in the queue, otherwise empty
    flash queues, and return the tuple of (last message, whether the message is
    a success or error message). Always return the success message if both
    success and error queues have messages.
    """
    errors = request.session.pop_flash(queue='error_queue')
    successes = request.session.pop_flash(queue='success_queue')
    return (successes[-1], True) if successes else \
            ((errors[-1], False) if errors else None)

def send_internal_email(subject, body):
    """
    Send an email to business@aerofs.com. Ignore errors.
    """
    fromEmail = 'Pyramid Server <business@aerofs.com>'
    toEmail = 'business@aerofs.com'

    log.info("send_internal_email:\n"
              "subject: {}\nbody: {}\n".format(subject, body))

    msg = MIMEText(body)
    msg['Subject'] = subject
    msg['From'] = fromEmail
    msg['To'] = toEmail

    # Send the message via our own SMTP server, but don't include the
    # envelope header.
    try:
        s = smtplib.SMTP('localhost')
        s.sendmail(fromEmail, [toEmail], msg.as_string())
        s.quit()
    except Exception, e:
        log.error("send_internal_email failed and ignored", exc_info=e)

def is_team_server_user_id(user_id):
    """
    Must be conssitent with UserID.java:TEAM_SERVER_PREFIX
    """
    return user_id.startswith(':')

def str2bool(v):
    if v == None:
        return False
    elif isinstance(v, bool):
        return v
    else:
        return v.lower() in ("yes", "true", "t", "1", "on")

def is_private_deployment(settings):
    return str2bool(settings.get('config.loader.is_private_deployment', False))

def is_mobile_disabled(settings):
    # only true if on private deployment
    return is_private_deployment(settings) and str2bool(settings.get('web.disable_download_mobile_client', False))

def is_linksharing_enabled(settings):
    return str2bool(settings.get('url_sharing.enabled', True));

def is_restricted_external_sharing_enabled(settings):
    return str2bool(settings.get('sharing_rules.restrict_external_sharing', False))

def add_routes(config, routes):
    """
    Add all the routes specified in routes to the Pyramid config. The URL path
    is set to identical to the route name.
    """
    for route in routes: config.add_route(route, route)

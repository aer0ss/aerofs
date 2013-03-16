"""
Helper functions for AeroFS website
"""

import re, logging
from error import error
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub, ADMIN

import smtplib
from email.mime.text import MIMEText

log = logging.getLogger("web")

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
        error("This email doesn't seem to be real.")

def domain_sanity_check(domain):
    """
    Performs a basic sanity check on a given domain name to verify that it could
    be valid. Returns true if the domain passes, false otherwise
    """
    return len(domain) > 3 and domain.find(' ') < 0 <= domain.find('.')

# TODO (WW) move this three functions to security/authentication related modules
def is_logged_in(request):
    return 'username' in request.session

def is_admin(request):
    return request.session['group'] == ADMIN

def get_session_user(request):
    return request.session['username']

def _is_ascii(string):
    """
    Checks if a given string is ascii (used by valid_password_test)
    """

    # http://stackoverflow.com/a/196391 (plus first comment) explains how to
    # detect non-ascii strings.
    try:
        string.encode("ascii")
    except UnicodeEncodeError:
        return False
    else:
        return True

def valid_password_test(request, password):
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
        return False, _("The password contains invalid characters.")
    else:
        return True, ""

def get_error(exception):
    """
    DEPRECATED: use error.sp_exception2error instead

    Returns an error message for the given protobuf exception. Expects the
    exception to be of type 'ExceptionReply'.
    """
    return str(exception.reply.message)

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
    request.session['SPCookie'] to be set (should be by login)
    """
    settings = request.registry.settings
    if 'SPCookie' in request.session: # attempt session recovery
        con = SyncConnectionService(settings['sp.url'], settings['sp.version'], request.session['SPCookie'])
    else:
        con = SyncConnectionService(settings['sp.url'], settings['sp.version'])
    return SPServiceRpcStub(con)

# Message reporting functions (for message bar)

def flash_error(request, error_msg):
    """
    Adds the given error message to the error message queue, which is dumped to
    the message bar the next time a page is rendered.
    """
    request.session.flash(error_msg, 'error_queue')

def errors_in_flash_queue(request):
    """
    Returns true if there are errors waiting in the error queue to be shown, and
    false if the queue is empty.
    """
    return len(request.session.peek_flash('error_queue')) > 0

def flash_success(request, success_msg):
    """
    Adds the given message to the success message queue, which is dumped to the
    message bar the next time a page is rendered.
    """
    request.session.flash(success_msg, 'success_queue')

def successes_in_flash_queue(request):
    """
    Returns true if there are success messages waiting in the success queue,
    and false if the queue is empty.
    """
    return len(request.session.peek_flash('success_queue')) > 0

def reload_auth_level(request):
    """
    Invalidate the auth level cached in the session and refetch from the database.
    """
    # N.B. an attacker could forge the group field in the cookie, but that would
    # only change the nav options displayed by the python. SP would still keep
    # all sensitive info private. Therefore keeping the authlevel here is fine.
    # We prefer to keep it here because we avoid an unnecessary call to SP
    # whenever we need to check authlevel.
    sp = get_rpc_stub(request)
    authlevel = int(sp.get_authorization_level().level)
    request.session['group'] = authlevel

def send_internal_email(subject, content):
    """
    Send an email to team@aerofs.com. Errors are ignored.
    """
    fromEmail = 'Pyramid Server <support@aerofs.com>'
    toEmail = 'team@aerofs.com'

    msg = MIMEText(content)
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
        log.error("send_internal_email failed and ignored: {}\n"
                  "subject: {} content: {}".format(subject, content, e))

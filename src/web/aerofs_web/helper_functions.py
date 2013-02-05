"""
Helper functions for AeroFS website
"""

import re
import logging
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub

log = logging.getLogger("web")

# Form validation functions

def userid_sanity_check(userid):
    """
    Performs a very basic validation of the given userid. Returns true if the
    userid passes, false otherwise
    """
    return re.match(r"[^@'\";]+@[^@'\";]+\.[^@'\";]+", userid)

def domain_sanity_check(domain):
    """
    Performs a basic sanity check on a given domain name to verify that it could
    be valid. Returns true if the domain passes, false otherwise
    """
    return len(domain) > 3 and domain.find(' ') < 0 and domain.find('.') >= 0

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
        return False, _("Passwords must be at least 6 characters.")
    elif not _is_ascii(password):
        return False, _("The password contains invalid characters.")
    else:
        return True, ""

# Exception processing functions
def get_error(exception): # parse RPC errors into something more user friendly
    """
    Returns an error message for the given protobuf exception. Expects the
    exception to be of type 'ExceptionReply'.
    """
    # TODO (WW) capitalize the first letter?
    return str(exception.reply.message)

def parse_rpc_error_exception(request, e):
    """
    Reads an exception of any sort generated when making an RPC call
    and returns an appropriate error message for that exception.
    """
    _ = request.translate
    if type(e) == ExceptionReply: # RPC Error
        log.error("RPC Error: " + get_error(e))
        return _("Error: ${error}", {'error': get_error(e)})
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

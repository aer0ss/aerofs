"""
Helper functions for AeroFS website
"""
from HTMLParser import HTMLParser
import os
import re
import logging
import markupsafe
import smtplib
import datetime
from email.mime.text import MIMEText
from aerofs_common.constants import DEFAULT_MIN_PASSWORD_LENGTH, DEFAULT_IS_NUMBERS_LETTERS_REQUIRED, CONFIG_COMPLETED_FLAG_FILE

from error import expected_error
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub

log = logging.getLogger(__name__)

# TODO (RD) consolidate this with the string in SubjectPermissions.java
GROUP_PREFIX = "g:"
HTML_PARSER = HTMLParser()

def is_configuration_completed():
    return os.path.exists(CONFIG_COMPLETED_FLAG_FILE)

# Form validation functions

# TODO (WW) move email checking to SP, or at least use error_on_invalid_email()
def is_valid_email(email):
    """
    Performs a very basic validation of the given userid. Returns true if the
    userid passes, false otherwise
    """
    return re.match(r"[^@'\"\s;<>]+@[^@'\"\s;<>]+\.[^@'\"\s;<>]+", email)

# TODO (WW) move email checking to SP
def error_on_invalid_email(email):
    if not is_valid_email(email):
        expected_error("This email doesn't seem to be valid.")

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
    except UnicodeDecodeError:
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

    min_password_length = request.registry.settings.get('password.restriction.min_password_length')
    is_numbers_letters_required = str2bool(request.registry.settings.get(
        'password.restriction.numbers_letters_required'))

    if not min_password_length:
        min_password_length = DEFAULT_MIN_PASSWORD_LENGTH

    if not is_numbers_letters_required:
        is_numbers_letters_required = DEFAULT_IS_NUMBERS_LETTERS_REQUIRED

    if is_numbers_letters_required:
        if len(password) < int(min_password_length) and not _password_has_alphanumeric_characters(password):
            return False, _("Password must be at least %s characters long and consist of alphanumeric characters. "
                            "(i.e. at least one letter and at least one number)." % min_password_length)
        elif len(password) < int(min_password_length):
            return False, _("Password must be at least %s characters long." % min_password_length)
        elif not _password_has_alphanumeric_characters(password):
            return False, _("Password must have alphanumeric characters. "
                            "(i.e. at least one letter and at least one number).")
        elif not _is_ascii(password):
            return False, _("The password contains invalid (non-ASCII) characters.")
        else:
            return True, ""
    else:
        if len(password) < int(min_password_length):
            return False, _("Password must be at least %s characters long." % min_password_length)
        elif not _is_ascii(password):
            return False, _("The password contains invalid (non-ASCII) characters.")
        else:
            return True, ""

def _password_has_alphanumeric_characters(password):
    return re.search(r"\d", password) and re.search(r"[a-zA-Z]", password)

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
        con = SyncConnectionService(settings['deployment.sp_server_uri'],
                                    settings['sp.version'],
                                    request.session['sp_cookies'])
    else:
        # attempt session recovery
        con = SyncConnectionService(settings['deployment.sp_server_uri'],
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

def send_sales_email(from_email, subject, body):
    """
    Send an email to sales@aerofs.com. Ignore errors.
    """
    to_email = 'sales@aerofs.com'

    log.info("send_sales_email:\n"
              "subject: {}\nbody: {}\n".format(subject, body))

    msg = MIMEText(body)
    msg['Subject'] = subject
    msg['From'] = from_email
    msg['To'] = to_email

    # Send the message via our own SMTP server, but don't include the
    # envelope header.
    try:
        s = smtplib.SMTP('localhost')
        s.sendmail(from_email, [to_email], msg.as_string())
        s.quit()
    except Exception, e:
        log.error("send_sales_email failed and ignored", exc_info=e)

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

def get_folder_invitation_count(request):
    sp = get_rpc_stub(request)
    return len(sp.list_pending_folder_invitations().invitation)

def get_deployment_secret(settings):
    secret_file = settings['deployment.secret_file']
    with open(secret_file) as f:
        return f.read().strip()

def is_mobile_disabled(settings):
    return str2bool(get_settings_nonempty(settings, 'web.disable_download_mobile_client', False))

def is_linksharing_enabled(settings):
    # Unfortunately, upgrading appliances will have an empty string for this property
    # and we want the default to be True here
    return str2bool(get_settings_nonempty(settings, 'url_sharing.enabled', True))

def is_restricted_external_sharing_enabled(settings):
    return str2bool(get_settings_nonempty(settings, 'sharing_rules.restrict_external_sharing', False))

def is_user_view_enabled_nonadmin(settings):
    return str2bool(get_settings_nonempty(settings, 'customization.enable_user_view', True))

def is_group_view_enabled_nonadmin(settings):
    return str2bool(get_settings_nonempty(settings, 'customization.enable_group_view', True))

def add_routes(config, routes):
    """
    Add all the routes specified in routes to the Pyramid config. The URL path
    is set to identical to the route name.
    """
    for route in routes: config.add_route(route, route)

def get_settings_nonempty(settings, key, default=None):
    str_value = settings.get(key, '')
    return str_value if str_value != '' else default

def get_days_until_license_expires(settings):
    expiry_date = datetime.datetime.strptime(get_settings_nonempty(settings, 'license_valid_until'), "%Y-%m-%d")
    today = datetime.datetime.today().date()
    start_of_today = datetime.datetime(year=today.year, month=today.month, day=today.day)
    days = (expiry_date - start_of_today).days
    return -1 if days < 0 else days

class CustomizableHTMLParser(HTMLParser):
    def __init__(self):
        HTMLParser.__init__(self)
        self.buf = []

    def handle_starttag(self, tag, attrs):
        if self.is_tag_supported(tag):
            self.buf.append(self.format_start_tag(tag, attrs))
        else:
            self.buf.append(markupsafe.escape(self.format_start_tag(tag, attrs)))

    def handle_endtag(self, tag):
        if self.is_tag_supported(tag):
            self.buf.append(self.format_end_tag(tag))
        else:
            self.buf.append(markupsafe.escape(self.format_end_tag(tag)))

    def handle_data(self, data):
        self.buf.append(data)

    def is_tag_supported(self, tag):
        return tag in self.get_supported_tags()

    def get_supported_tags(self):
        return []

    def format_start_tag(self, tag, attrs):
        return "<{}{}{}>".format(tag, ' ' if len(attrs) > 0 else '', self.format_attrs_list(attrs))

    def format_attrs_list(self, attrs):
        return " ".join(map(lambda p: "{}='{}'".format(p[0], p[1]), attrs))

    def format_end_tag(self, tag):
        return "</{}>".format(tag)

    def get_result(self):
        return " ".join(self.buf)


class CustomHTMLParser(CustomizableHTMLParser):
    def get_supported_tags(self):
        return {'a'}

def escape_html_except_anchors(text):
    parser = CustomHTMLParser()
    parser.feed(text)
    return parser.get_result()
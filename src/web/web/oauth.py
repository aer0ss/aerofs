#
# This file contains utility functions for AeroFS OAuth clients
#
import logging
import re
from web import util
from web.util import flash_error

log = logging.getLogger(__name__)


# The URL to Bifrost, i.e. the OAuth server
def get_bifrost_url(request):
    return request.registry.setting["deployment.oauth_server_uri"]

def is_builtin_client_id(client_id):
    """
    Some client IDs are for internal use and not to exposed to end users, such
    as 'aerofs-ios.' This method returns whether the given client ID is internal.
    """
    return len(client_id) != 36

def is_valid_access_token(token):
    return re.match("^[0-9a-f]{32}$", token)

def is_valid_non_builtin_client_id(client_id):
    return re.match("^[\-0-9a-f]{36}$", client_id)

def flash_error_for_bifrost_response(request, response):
    flash_error(request, _get_error_message_for_bifrost_resonse(response))


def raise_error_for_bifrost_response(response):
    util.error(_get_error_message_for_bifrost_resonse(response))


def _get_error_message_for_bifrost_resonse(response):
    log.error('bifrost error: {} text: {}'.format(response.status_code, response.text))
    return 'An error occurred. Please try again. The error is: {} {}'\
            .format(response.status_code, response.text)

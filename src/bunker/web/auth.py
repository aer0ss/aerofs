# Authentication / authorization related utility functions.
#
# N.B. an attacker could forge the auth level field in the cookie, but because
# login_view.py:get_principals() calls _get_auth_level() every time
# a request is made, it is not a problem.
#
import logging
from pyramid.security import authenticated_userid
from web.license import is_license_shasum_valid, get_license_shasum_from_session

GROUP_ID_MAINTAINERS = 'group:maintainers'

log = logging.getLogger(__name__)


def is_authenticated(request):
    """
    This method returns True iff. remember() has been called
    """
    return authenticated_userid(request)


def get_principals(authed_userid, request):
    """
    This method is used as the callback for SessionAuthenticationPolicy().
    This is THE function that dictates authorization.
    """
    shasum = get_license_shasum_from_session(request)
    if shasum and is_license_shasum_valid(request, shasum):
        return [GROUP_ID_MAINTAINERS]
    else:
        return []

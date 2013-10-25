import logging
from aerofs_sp.gen.common_pb2 import PBException
from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.scrypt import scrypt

from web.util import *

# URL param keys.
URL_PARAM_FORM_SUBMITTED = 'form_submitted'
URL_PARAM_EMAIL = 'email'
URL_PARAM_PASSWORD = 'password'
URL_PARAM_REMEMBER_ME = 'remember_me'

# N.B. This parameter is also used in aerofs.js. Remember to update this and all
# other references to this parameter when modifying handling of the next parameter.
URL_PARAM_NEXT = 'next'

log = logging.getLogger(__name__)

def get_group(userid, request):
    # Must reload auth level on every request, because not all pages that
    # require authentication make SP calls.
    reload_auth_level(request)

    return [request.session.get('group')]

def _get_next_url(request):
    """
    Return the value of the 'next' parameter in the request. Return the
    dashboard home URL if the parameter is absent. It never returns None
    """
    next_url = request.params.get(URL_PARAM_NEXT)
    return next_url if next_url else request.route_path('dashboard_home')

def resolve_next_url(request):
    """
    Return the value of the 'next' parameter in the request. Return
    dashboard_home if the next param is not set. Always prefix with the host URL
    to prevent attackers to insert arbitrary URLs in the parameter, e.g.:
    aerofs.com/login?next=http%3A%2F%2Fcnn.com.
    """
    next_url = _get_next_url(request)

    # If _get_next_url()'s return value doesn't include a leading slash, add it.
    # This is to prevent the next_url being things like .cnn.com and @cnn.com
    if next_url[:1] != '/': next_url = '/' + next_url

    return request.host_url + next_url

def _is_openid_enabled(request):
    """
    True if the local deployment allows OpenID authentication
    """
    return is_private_deployment(request.registry.settings) \
        and request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'openid'

def _is_external_cred_enabled(settings):
    """
    True if the server allows external authentication, usually LDAP.
    This determines whether we should scrypt the user password.
    """
    return is_private_deployment(settings) \
        and settings.get('lib.authenticator', 'local_credential').lower() == 'external_credential'

def _format_password(settings, password, login):
    """
    If the server configuration expects an scrypt'ed credential for this user, do so here;
    otherwise return the cleartext password as provided.
    NOTE the logic embedded here is also found in IUserFilter/UserFilterFactory; any
    changes need to be reflected in both places.
    """
    if _is_external_cred_enabled(settings):
        internal_pattern = settings.get('internal_email_pattern')
        if internal_pattern is None or re.compile(internal_pattern).search(login) is not None:
            return str(password)

    return scrypt(password, login)

def _do_login(request):
    """
    Log in the user. Return HTTPFound if login is successful; otherwise call
    flash_error() to insert error messages and then return None.
    """
    _ = request.translate

    # Remember to normalize the email address.
    login = request.params[URL_PARAM_EMAIL]
    password = request.params[URL_PARAM_PASSWORD]
    hashed_password = _format_password(request.registry.settings, password, login)
    stay_signed_in = URL_PARAM_REMEMBER_ME in request.params
    try:
        try:
            headers = _log_in_user(request, login, hashed_password, stay_signed_in)
            redirect = resolve_next_url(request)
            log.debug(login + " logged in. redirect to " + redirect)
            return HTTPFound(location=redirect, headers=headers)
        except ExceptionReply as e:
            if e.get_type() == PBException.BAD_CREDENTIAL:
                log.warn(login + " attempts to login w/ bad password")
                flash_error(request, _("Email or password is incorrect."))
            elif e.get_type() == PBException.EMPTY_EMAIL_ADDRESS:
                flash_error(request, _("The email address cannot be empty."))
            else:
                raise e
    except Exception as e:
        log.error("error during logging in:", exc_info=e)
        support_email = request.registry.settings.get(
                'base.www.support_email_address', 'support@aerofs.com')
        flash_error(request, _("An error occurred processing your request." +
                " Please try again later. Contact " + support_email + " if the" +
                " problem persists."))


@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login_view(request):
    _ = request.translate
    settings = request.registry.settings
    next_url = _get_next_url(request)

    if URL_PARAM_FORM_SUBMITTED in request.params:
        ret = _do_login(request)
        if ret: return ret

    openid_enabled = _is_openid_enabled(request)
    # if openid_enabled is false we don't need to do any of the following. :(
    openid_url = "{0}?{1}".format(request.route_url('login_openid_begin'),
                                  url.urlencode({'next': next_url}))
    openid_identifier = settings.get('openid.service.identifier', 'OpenID')
    external_hint = settings.get('openid.service.external.hint',
                                 'AeroFS user without an OpenID account?')

    login = request.params.get(URL_PARAM_EMAIL)
    if not login: login = ''

    return {
        'url_param_form_submitted': URL_PARAM_FORM_SUBMITTED,
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'url_param_next': URL_PARAM_NEXT,
        'openid_enabled': openid_enabled,
        'openid_url': openid_url,
        'openid_service_identifier': openid_identifier,
        'openid_service_external_hint': external_hint,
        'login': login,
        'next': next_url,
    }

def _log_in_user(request, login, creds, stay_signed_in):
    """
    Logs in the given user with the given hashed password (creds) and returns a
    set of headers to create a session for the user. Could potentially throw any
    protobuf exception that SP throws.
    """

    # ignore any session data that may be saved
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    sp.sign_in(login, creds)

    if stay_signed_in:
        log.debug("Extending session")
        sp.extend_session()

    request.session['sp_cookies'] = con._session.cookies
    request.session['username'] = login
    request.session['team_id'] = sp.get_organization_id().org_id
    reload_auth_level(request)

    return remember(request, login)

@view_config(
    route_name = 'logout',
    permission = 'user'
)
def logout_view(request):
    return HTTPFound(
        location=request.route_path('login'),
        headers=logout(request)
    )

def logout(request):
    sp = get_rpc_stub(request)
    try:
        sp.sign_out()
    except Exception:
        # Server errors don't matter when logging out.
        pass

    # Delete session information from client cookies.
    request.session.delete()

    return forget(request)

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
URL_PARAM_NEXT = 'next' # N.B. the string "next" is also used in aerofs.js.


log = logging.getLogger(__name__)

def groupfinder(userid, request):
    # Must reload auth level on every request, because not all pages that
    # require authentication make SP calls.
    reload_auth_level(request)

    return [request.session.get('group')]

def get_next_url(request):
    """
    Return a value for the 'next' parameter; which is dashboard_home
    if the next param is not set (or set to a login page).
    Never redirect to the login page (in any of its forms).
    Handles null or empty requested_url.
    """
    _next = request.params.get('next')

    if not _next:
        _next = request.url
        login_urls = [
            request.resource_url(request.context, ''),
            request.resource_url(request.context, 'login'),
            request.resource_url(request.context, 'login_openid_begin'),
            request.resource_url(request.context, 'login_openid_complete'),
        ]

        if len(_next) == 0 or _next in login_urls:
            _next = request.route_path('dashboard_home')
    return _next

def _is_openid_enabled(request):
    """
    True if the local deployment allows OpenID authentication
    """
    return is_private_deployment(request.registry.settings) \
        and request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'openid'

def _is_external_cred_enabled(request):
    """
    True if the server allows external authentication, usually LDAP.
    This determines whether we should scrypt the user password.
    """
    return is_private_deployment(request.registry.settings) \
        and request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'external_credential'

def _format_password(request, password, login):
    """
    If the server configuration expects an scrypt'ed credential for this user, do so here;
    otherwise return the cleartext password as provided.
    NOTE the logic embedded here is also found in IUserFilter/UserFilterFactory; any
    changes need to be reflected in both places.
    """
    if _is_external_cred_enabled(request):
        internal_pattern = request.registry.settings.get('internal_email_pattern')
        if internal_pattern is not None:
            reg = re.compile(internal_pattern)
            if reg.search(login) is not None:
                return str(password)
    return scrypt(password, login)

@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login_view(request):
    # FIXME: a handful of non-idiomatic python follows. Please clean up when the skies are clear.
    _ = request.translate
    settings = request.registry.settings
    next = get_next_url(request)

    login = ''
    # N.B. the all following parameter keys are used by signup.mako as well.
    # Keep them consistent!
    if URL_PARAM_FORM_SUBMITTED in request.params:
        # Remember to normalize the email address.
        login = request.params[URL_PARAM_EMAIL]
        password = request.params[URL_PARAM_PASSWORD]
        hashed_password = _format_password(request, password, login)
        stay_signed_in = URL_PARAM_REMEMBER_ME in request.params

        try:
            try:
                headers = _log_in_user(request, login, hashed_password, stay_signed_in)
                log.debug(login + " logged in")
                return HTTPFound(location=next, headers=headers)
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
            support_email = settings.get('base.www.support_email_address', 'support@aerofs.com')
            flash_error(request, _("An error occurred processing your request." +
                   " Please try again later. Contact " + support_email + " if the" +
                   " problem persists."))

    openid_enabled = _is_openid_enabled(request)
    # if openid_enabled is false we don't need to do any of the following. :(
    openid_url = "{0}?{1}".format(request.route_url('login_openid_begin'), url.urlencode({'next' : next}))
    openid_identifier = settings.get('openid.service.identifier', 'OpenID')
    external_hint = settings.get('openid.service.external.hint', 'AeroFS user without an OpenID account?')

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
        'next': next,
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

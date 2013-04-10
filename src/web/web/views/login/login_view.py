import logging
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.scrypt import scrypt

from web.util import *

log = logging.getLogger(__name__)

def groupfinder(userid, request):
    return [request.session.get('group')]

def log_in_user(request, login, creds, stay_signed_in):
    """
    Logs in the given user with the given hashed password (creds) and returns a
    set of headers to create a session for the user. Could potentially throw any
    protobuf exception that SP throws.
    """

    # ignore any session data that may be saved
    settings = request.registry.settings
    con = SyncConnectionService(settings['sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    log.debug("Log in user " + str(login))
    sp.sign_in(login, creds)

    if stay_signed_in:
        log.debug("Extending session.")
        sp.extend_session()

    request.session['cookies'] = con._session.cookies
    request.session['username'] = login

    reload_auth_level(request)

    return remember(request, login)

# URL param keys.
URL_PARAM_FORM_SUBMITTED = 'form_submitted'
URL_PARAM_EMAIL = 'email'
URL_PARAM_PASSWORD = 'password'
URL_PARAM_REMEMBER_ME = 'remember_me'
URL_PARAM_NEXT = 'next' # N.B. the string "next" is also used in aerofs.js.

@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login(request):
    _ = request.translate

    next = request.params.get('next')

    if not next:
        next = request.url
        if next == request.resource_url(request.context, 'login'):
            # Never redirect to the login page itself.
            next = request.route_path('dashboard_home')

    login = ''
    # N.B. the all following parameter keys are used by signup.mako as well.
    # Keep them consistent!
    if URL_PARAM_FORM_SUBMITTED in request.params:
        # Remember to normalize the email address.
        login = request.params[URL_PARAM_EMAIL]
        password = request.params[URL_PARAM_PASSWORD]
        hashed_password = scrypt(password, login)
        stay_signed_in = URL_PARAM_REMEMBER_ME in request.params

        try:
            try:
                headers = log_in_user(request, login, hashed_password, stay_signed_in)
                return HTTPFound(location=next, headers=headers)
            except ExceptionReply as e:
                if e.get_type() == PBException.BAD_CREDENTIAL:
                    flash_error(request, _("Email or password is incorrect."))
                elif e.get_type() == PBException.EMPTY_EMAIL_ADDRESS:
                    flash_error(request, _("The email address cannot be empty."))
                else:
                    raise e
        except Exception as e:
            log.error("error during logging in:", exc_info=e)
            flash_error(request, _("An error occurred processing your request." +
                   " Please try again later. Contact support@aerofs.com if the" +
                   " problem persists."))

    return {
        'url_param_form_submitted': URL_PARAM_FORM_SUBMITTED,
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'url_param_next': URL_PARAM_NEXT,
        'login': login,
        'next': next,
    }

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

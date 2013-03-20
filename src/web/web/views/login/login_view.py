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

    request.session['SPCookie'] = con._cookies
    request.session['username'] = login

    reload_auth_level(request)

    return remember(request, login)

@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login(request):
    _ = request.translate
    referrer = request.url
    if referrer == request.resource_url(request.context, 'login'):
        # Never use the login form itself as referrer.
        referrer = '/'

    next = request.params.get('next') or referrer
    login = ''

    if 'form_submitted' in request.params:
        # Remember to normalize the email address.
        login = request.params['login']
        password = request.params['password']
        hashed_password = scrypt(password, login)
        stay_signed_in = 'stay_signed_in' in request.params

        try:
            try:
                headers = log_in_user(request, login, hashed_password, stay_signed_in)
                return HTTPFound(location=next, headers=headers)
            except ExceptionReply as e:
                if e.get_type() == PBException.BAD_CREDENTIAL:
                    flash_error(request, _("Email or password is incorrect."))
                else:
                    raise e
        except Exception as e:
            log.error("error during logging in:", exc_info=e)
            flash_error(request, _("An error occurred processing your request." +
                   " Please try again later. Contact support@aerofs.com if the" +
                   " problem persists."))

    return {
        'login': login,
        'next': next
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

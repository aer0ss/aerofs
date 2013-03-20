import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, authenticated_userid, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config, forbidden_view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.scrypt import scrypt

from web.util import *

'''
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
'''

log = logging.getLogger("web")

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

@forbidden_view_config(
    renderer="forbidden.mako"
)
def forbidden_view(request):
    # do not allow a user to login if they are already logged in
    if authenticated_userid(request): return {}

    # So that we don't get annoying next=%2F in the url when we click on the home button.
    next = request.path.strip()
    if next and next != '/':
        loc = request.route_url('login', _query=(('next', next),))
    else:
        loc = request.route_url('login')

    return HTTPFound(location=loc)

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
    did_fail = False
    error = ''

    if 'form.submitted' in request.params:
        login = request.params['login']

        if not userid_sanity_check(login):
            error = _("Invalid user id")
            did_fail = True
        else:
            password = request.params['password']
            hashedPassword = scrypt(password, login) # hash password before sending it to sp

            staySignedIn = False
            if "staySignedIn" in request.params:
                staySignedIn = True

            try:
                headers = log_in_user(request, login, hashedPassword, staySignedIn)
                return HTTPFound(location=next, headers=headers)
            except Exception as e:
                error = parse_rpc_error_exception(request, e)
                did_fail=True

    if did_fail:
        flash_error(request, _("Failed login attempt: ${error}", {'error': error}))
    return {
        'login': login,
        'next': next,
        'failed_attempt': did_fail,
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

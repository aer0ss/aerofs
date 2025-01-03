import logging

from pyramid.security import authenticated_userid
from pyramid.view import view_config

from web.analytics import send_analytics_event
from web.util import get_rpc_stub
from web.oauth import get_bifrost_client

log = logging.getLogger(__name__)


def create_new_settings_token(request):
    client_id = 'aerofs-settings'
    client_secret = request.registry.settings["oauth.settings_client_secret"]
    # Explicitly request all the scopes, except organization.admin.
    sp_client = get_rpc_stub(request)
    access_code = sp_client.get_access_code().accessCode
    bifrost_client = get_bifrost_client(request)
    return bifrost_client.get_new_oauth_token(access_code, client_id, client_secret, scopes=[
        'files.read',
        'files.write',
        'files.appdata',
        'user.read',
        'user.write',
        'user.password',
        'acl.read',
        'acl.write',
        'acl.invitations',
        'groups.read',
    ])


@view_config(
    route_name='settings',
    permission='user',
    renderer='settings.mako',
    request_method='GET'
)
def settings(request):
    send_analytics_event(request, "ACTIVE_USER")
    sp = get_rpc_stub(request)
    reply = sp.get_user_preferences(None)
    can_has_tfa = sp.get_two_factor_setup_enforcement().level > 0
    user_settings_token = sp.get_user_settings_token().token

    return {
        'first_name': reply.first_name,
        'last_name': reply.last_name,
        'signup_date': reply.signup_date,
        'userid': authenticated_userid(request),
        'two_factor_enforced': reply.two_factor_enforced,
        'can_has_tfa': can_has_tfa,
        'user_settings_token': user_settings_token
    }


@view_config(
    route_name='json_create_user_settings_access_token',
    permission='user',
    renderer='json',
    request_method='POST'
)
def json_create_user_settings_access_token(request):
    sp = get_rpc_stub(request)
    token = create_new_settings_token(request)
    sp.set_user_settings_token(token)
    return {}


@view_config(
    route_name='json_delete_user_settings_access_token',
    permission='user',
    renderer='json',
    request_method='POST'
)
def json_delete_user_settings_access_token(request):
    sp = get_rpc_stub(request)
    user_settings_token = sp.get_user_settings_token().token
    # If the user has a token delete it in bifrost.
    if len(user_settings_token) > 0:
        bifrost_client = get_bifrost_client(request)
        bifrost_client.delete_access_token(user_settings_token)
        bifrost_client.flash_on_error(request)
        # Delete the persistent store of the token on SP.
        sp.delete_user_settings_token()
    return {}


@view_config(
    route_name='json_send_password_reset_email',
    permission='user',
    renderer='json',
    request_method='POST'
)
def json_send_password_reset_email(request):
    sp = get_rpc_stub(request)
    sp.send_password_reset_email(authenticated_userid(request))


@view_config(
    route_name='json_delete_user',
    permission='user',
    renderer='json',
    request_method='POST'
)
def json_delete_user(request):
    sp = get_rpc_stub(request)
    sp.deactivate_user(authenticated_userid(request), False)


@view_config(
    route_name='json_set_full_name',
    permission='user',
    renderer='json',
    request_method='POST'
)
def json_set_full_name(request):
    sp = get_rpc_stub(request)
    sp.set_user_preferences(authenticated_userid(request),
                            request.params['first-name'],
                            request.params['last-name'],
                            None, None)

import logging
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from web.util import get_rpc_stub

log = logging.getLogger(__name__)


@view_config(
    route_name='settings',
    permission='user',
    renderer='settings.mako',
    request_method='GET'
)
def settings(request):
    sp = get_rpc_stub(request)
    reply = sp.get_user_preferences(None)
    can_has_tfa = sp.get_two_factor_setup_enforcement().level > 0
    return {
        'first_name': reply.first_name,
        'last_name': reply.last_name,
        'signup_date': reply.signup_date,
        'userid': authenticated_userid(request),
        'two_factor_enforced': reply.two_factor_enforced,
        'can_has_tfa': can_has_tfa
    }

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

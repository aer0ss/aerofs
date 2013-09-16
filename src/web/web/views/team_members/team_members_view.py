import logging, urllib
from cgi import escape
from pyramid.httpexceptions import HTTPOk, HTTPMovedPermanently
from pyramid.view import view_config
from pyramid.renderers import render
from aerofs_sp.gen.common_pb2 import PBException
from web import util
from web.sp_util import exception2error
from web.util import *
from web.views.payment import stripe_util
from aerofs_sp.gen.sp_pb2 import USER, ADMIN

# URL param keys
URL_PARAM_USER = 'user'
URL_PARAM_LEVEL = 'level'
URL_PARAM_FULL_NAME = 'full_name'

log = logging.getLogger(__name__)

@view_config(
    route_name = 'team_members',
    renderer = 'team_members.mako',
    permission = 'admin'
)
def team_members(request):
    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    invited_users = sp.list_organization_invited_users()
    return {
        'stripe_publishable_key': stripe_util.STRIPE_PUBLISHABLE_KEY,
        'url_param_user': URL_PARAM_USER,
        'url_param_level': URL_PARAM_LEVEL,
        'url_param_stripe_card_token': stripe_util.URL_PARAM_STRIPE_CARD_TOKEN,
        'invited_users': invited_users.user_id,
        'admin_level': ADMIN,
        'user_level':USER
    }

@view_config(
    route_name = 'json.list_team_members',
    renderer = 'json',
    permission = 'admin'
)
def json_list_team_members(request):
    echo = str(request.GET['sEcho'])
    count = int(request.GET['iDisplayLength'])
    offset = int(request.GET['iDisplayStart'])
    session_user = get_session_user(request)

    sp = get_rpc_stub(request)
    reply = sp.list_organization_members(count, offset)

    data = [{
                'name': _render_full_name(ul.user, session_user),
                'label': _render_label(ul.level),
                'email': escape(ul.user.user_email),
                'options': _render_user_options_link(request, ul, session_user)
            } for ul in reply.user_and_level]

    return {
        'sEcho': echo,
        'iTotalRecords': reply.total_count,
        "iTotalDisplayRecords": reply.total_count,
        'aaData': data
    }

def _render_label(level):
    hidden = '' if level == ADMIN else 'hidden'
    return '<span class="admin_label label {} tooltip_admin">admin</span>'.format(hidden)

def _render_user_options_link(request, user_and_level, session_user):
    user = user_and_level.user
    user_is_session_user = (user.user_email == session_user)

    if user_is_session_user:
        shared_folders_url = request.route_url('my_shared_folders')
        devices_url = request.route_url('my_devices')
    else:
        params = {URL_PARAM_USER: user.user_email,
                  URL_PARAM_FULL_NAME: user.first_name + " " + user.last_name}

        shared_folders_url = request.route_url('user_shared_folders', _query=params)
        devices_url = request.route_url('user_devices', _query=params)

    return render('actions_menu.mako', {
                                        'user_is_session_user': user_is_session_user,
                                        'shared_folders_url': shared_folders_url,
                                        'devices_url': devices_url,
                                        'email': user.user_email,
                                        'is_admin': (user_and_level.level == ADMIN),
                                        'is_private': is_private_deployment(request.registry.settings),
        },request=request)


def _render_full_name(user, session_user):
    return "me" if user.user_email == session_user else user.first_name + " " + user.last_name


@view_config(
    route_name = 'json.invite_user',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_invite_user(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]
    error_on_invalid_email(user)

    sp = get_rpc_stub(request)

    # invite the user
    reply = exception2error(sp.invite_to_organization, user, {
            # TODO (WW) change to ALREADY_MEMBER?
            # See also shared_folders_view.py:json_add_shared_folder_perm()
            PBException.ALREADY_EXIST:
                _("The user is already a member of your team."),
            PBException.EMPTY_EMAIL_ADDRESS:
                _("The email address can't be empty."),
            PBException.ALREADY_INVITED:
                _("The user has already been invited to your team."),
            PBException.NO_STRIPE_CUSTOMER_ID:
                _("Payment is required to invite more users.")
        }
    )

    stripe_util.update_stripe_subscription(reply.stripe_data)

    return HTTPOk()

@view_config(
    route_name = 'json.delete_team_invitation',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_delete_team_invitation(request):
    user = request.params[URL_PARAM_USER]
    sp = get_rpc_stub(request)
    stripe_data = sp.delete_organization_invitation_for_user(user).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)
    return HTTPOk()

@view_config(
    route_name = 'json.set_level',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_set_level(request):
    user = request.params[URL_PARAM_USER]
    level = int(request.params[URL_PARAM_LEVEL])
    sp = get_rpc_stub(request)
    sp.set_authorization_level(user, level)
    return HTTPOk()

@view_config(
    route_name = 'json.remove_from_team',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_remove_from_team(request):
    user = request.params[URL_PARAM_USER]
    sp = get_rpc_stub(request)
    stripe_data = sp.remove_user_from_organization(user).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)
    return HTTPOk()

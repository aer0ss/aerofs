import logging

import markupsafe
from pyramid.httpexceptions import HTTPOk, HTTPBadRequest
from pyramid.security import authenticated_userid
from pyramid.view import view_config
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.gen.sp_pb2 import USER, ADMIN

from web import util
from web.oauth import get_privileged_bifrost_client
from web.sp_util import exception2error
from web.util import error_on_invalid_email, get_rpc_stub, str2bool, is_restricted_external_sharing_enabled
from web.auth import is_admin
from web.views.payment import stripe_util
from web.views.org_groups.org_groups_view import json_list_org_groups

# URL param keys
URL_PARAM_USER = 'user'
URL_PARAM_LEVEL = 'level'
URL_PARAM_FULL_NAME = 'full_name'
URL_PARAM_ERASE_DEVICES = 'erase_devices'

PAGE_LIMIT = 20
# we currently show a maximum of 6 entries
AUTOCOMPLETE_ENTRIES = 6

log = logging.getLogger(__name__)

@view_config(
    route_name = 'org_users',
    renderer = 'org_users.mako',
    permission = 'user'
)
def org_users(request):
    return {
        'admin_level': ADMIN,
        'user_level': USER,
        'pagination_limit': PAGE_LIMIT
    }

@view_config(
    route_name = 'json.list_org_invitees',
    renderer = 'json',
    http_cache = 0,
    permission = 'admin'
)
def json_list_org_invitees(request):
    sp = util.get_rpc_stub(request)
    reply = sp.list_organization_invited_users()
    return {
        'invitees': [{
            'email': email
        } for email in reply.user_id]
    }

@view_config(
    route_name = 'json.list_org_users',
    renderer = 'json',
    http_cache = 0,
    permission = 'user'
)
def json_list_org_users(request):
    count = int(request.params.get('count', PAGE_LIMIT))
    offset = int(request.params.get('offset', 0))
    substring = request.params.get('substring', None)

    session_user = authenticated_userid(request)

    sp = get_rpc_stub(request)
    reply = sp.list_organization_members(count, offset, substring)

    use_restricted = is_restricted_external_sharing_enabled(request.registry.settings)
    if use_restricted:
        wl_reply = sp.list_whitelisted_users()
        publishers = set([u.user_email for u in wl_reply.user])
    else:
        # if restricted external sharing is not being used, pretend the list
        # of publishers is empty so all of the labels are hidden
        publishers = set()

    data = [{
                'first_name': ul.user.first_name,
                'last_name': ul.user.last_name,
                'is_admin': ul.level == ADMIN,
                'is_publisher': ul.user.user_email in publishers,
                'email': markupsafe.escape(ul.user.user_email),
                'has_two_factor': ul.user.two_factor_enforced,
                'name': ul.user.first_name + ' ' + ul.user.last_name + ' (' + markupsafe.escape(ul.user.user_email) + ')'
            } for ul in reply.user_and_level]


    return {
        'total': reply.total_count,
        'pagination_limit': PAGE_LIMIT,
        'data': data,
        'use_restricted': use_restricted,
        'me': session_user
    }

@view_config(
    route_name = 'json.search_org_users',
    renderer = 'json',
    permission = 'user'
)
def json_search_org_users(request):
    count = int(request.params.get('count', AUTOCOMPLETE_ENTRIES))
    offset = int(request.params.get('offset', 0))
    substring = request.params.get('substring', None)

    if not count or not substring:
        return HTTPBadRequest()

    sp = get_rpc_stub(request)
    reply = sp.search_organization_users(count, offset, substring)

    users = [{
                'first_name': user.first_name,
                'last_name': user.last_name,
                'email': markupsafe.escape(user.user_email),
                'name': user.first_name + ' ' + user.last_name + ' (' + markupsafe.escape(user.user_email) + ')'
             } for user in reply.matching_users]
    return {
        'results': users
    }

@view_config(
    route_name = 'json.list_org_users_and_groups',
    renderer = 'json',
    permission = 'user'
)
def json_list_org_users_and_groups(request):
    user_data = json_search_org_users(request)['results'] or []
    group_data = json_list_org_groups(request)['groups'] or []
    data = group_data + user_data
    return {
        'results': data
    }

@view_config(
    route_name = 'json.invite_user',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_invite_user(request):
    _ = request.translate

    user = request.json_body[URL_PARAM_USER]
    error_on_invalid_email(user)

    sp = get_rpc_stub(request)

    # invite the user
    reply = exception2error(sp.invite_to_organization, user, {
            # TODO (WW) change to ALREADY_MEMBER?
            # See also shared_folders_view.py:json_add_shared_folder_perm()
            PBException.ALREADY_EXIST:
                _("The user is already a member of your organization."),
            PBException.EMPTY_EMAIL_ADDRESS:
                _("The email address can't be empty."),
            PBException.ALREADY_INVITED:
                _("The user has already been invited to your organization."),
            PBException.NO_STRIPE_CUSTOMER_ID:
                _("Payment is required to invite more users. You can enable payment by going to your organization's Settings.") if is_admin(request) else
                _("Payment is required to invite more users. Please ask your organization admin to enable payments.")
        }
    )

    stripe_util.update_stripe_subscription(reply.stripe_data)

    return {
        "locally_managed": reply.locally_managed
    }

@view_config(
    route_name = 'json.delete_org_invitation',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_delete_org_invitation(request):
    user = request.json_body[URL_PARAM_USER]
    sp = get_rpc_stub(request)
    stripe_data = sp.delete_organization_invitation_for_user(user).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)
    return HTTPOk()

@view_config(
    route_name = 'json.set_auth_level',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_set_auth_level(request):
    user = request.json_body[URL_PARAM_USER]
    level = int(request.json_body[URL_PARAM_LEVEL])
    sp = get_rpc_stub(request)

    # When demoting a user, remove any admin tokens they may have auth'ed:
    log.warn('set auth level to %' + str(level))
    bifrost_client = get_privileged_bifrost_client(request, service_name="web")
    r = bifrost_client.delete_delegated_tokens(user)
    if not r.ok:
        log.error('bifrost returned error:' + str(r))
        bifrost_client.flash_on_error(request, r)

    sp.set_authorization_level(user, level)
    return HTTPOk()

@view_config(
    route_name = 'json.remove_user',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_remove_user(request):
    user = request.json_body[URL_PARAM_USER]
    sp = get_rpc_stub(request)
    stripe_data = sp.remove_user_from_organization(user).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)
    return HTTPOk()

@view_config(
    route_name = 'json.deactivate_user',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_deactivate_user(request):
    user = request.json_body[URL_PARAM_USER]
    erase_devices = str2bool(request.json_body[URL_PARAM_ERASE_DEVICES])

    bifrost_client = get_privileged_bifrost_client(request, service_name="web")
    r = bifrost_client.delete_all_tokens(user)
    if not r.ok:
        log.error('bifrost returned error:' + str(r))
        bifrost_client.flash_on_error(request, r)

    sp = get_rpc_stub(request)
    stripe_data = sp.deactivate_user(user, erase_devices).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)
    return HTTPOk()

@view_config(
    route_name = 'json.set_publisher_status',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_set_publisher_status(request):
    user = request.json_body[URL_PARAM_USER]
    is_publisher = request.json_body['is_publisher']
    sp = get_rpc_stub(request)
    if is_publisher:
        sp.add_user_to_whitelist(user)
    else:
        sp.remove_user_from_whitelist(user)
    return HTTPOk()

@view_config(
    route_name = 'json.disable_two_factor',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST',
)
def json_disable_two_factor(request):
    user = request.params[URL_PARAM_USER]
    sp = get_rpc_stub(request)
    sp.set_two_factor_enforcement(False, None, user)
    return HTTPOk()

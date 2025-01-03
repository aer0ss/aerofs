import logging
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPOk, HTTPFound
from aerofs_sp.gen.common_pb2 import PBException
from web import util
from web.analytics import send_analytics_event
from web.util import is_group_view_enabled_nonadmin
from web.auth import is_admin
from web.sp_util import exception2error
from aerofs_common.constants import PAGE_LIMIT

log = logging.getLogger(__name__)

@view_config(
    route_name = 'org_groups',
    renderer = 'groups.mako',
    permission = 'user'
)
def org_groups(request):
    settings = request.registry.settings

    # determine if the groups view should be available to non-admins
    hide_groups_nonadmin = not is_group_view_enabled_nonadmin(settings)
    user_not_admin = not is_admin(request)

    # redirect to files if the user has nothing to do here
    if hide_groups_nonadmin and user_not_admin:
        return HTTPFound(location=request.route_path('files'))
    else:
        send_analytics_event(request, "ACTIVE_USER")
        sp = util.get_rpc_stub(request)
        reply = sp.get_org_preferences()

        return {
            'pagination_limit': PAGE_LIMIT,
            'member_limit': int(settings.get('sp.max.membership.group', '50')),
            'groupsyncing_enabled': settings.get('ldap.groupsyncing.enabled', False) and
                settings.get('lib.authenticator', "").upper() == 'EXTERNAL_CREDENTIAL'
        }

def _get_members(request, group_id):
    sp = util.get_rpc_stub(request)
    reply = sp.list_group_members(group_id)
    return [{
        'first_name': member.first_name,
        'last_name': member.last_name,
        'email': member.user_email
    } for member in reply.users]


@view_config(
    route_name = 'json.list_group_members',
    renderer = 'json',
    permission = 'user',
    request_method = 'GET'
)
def json_list_group_members(request):
    group_id = int(request.params.get('id', None))
    return {
        'members': _get_members(request, group_id)
    };

@view_config(
    route_name = 'json.list_org_groups',
    renderer = 'json',
    permission = 'user',
    request_method = 'GET'
)
def json_list_org_groups(request):
    '''List groups in this organization.
    Can optionally filter by substring in the group name.'''
    count = int(request.params.get('count', PAGE_LIMIT))
    offset = int(request.params.get('offset', 0))
    substring = request.params.get('substring', None)

    sp = util.get_rpc_stub(request)
    reply = sp.list_groups(count, offset, substring)
    groups = [{
        'id': group.group_id,
        'name': group.common_name,
        'is_externally_managed': group.externally_managed,
        'members': _get_members(request, group.group_id)
    } for group in reply.groups]
    return {
        'total': reply.total_count,
        'groups': groups
    }

@view_config(
    route_name = 'json.add_org_group',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_add_org_group(request):
    _ = request.translate
    sp = util.get_rpc_stub(request)
    common_name = request.json_body['name']
    members = request.json_body['members']

    # make group
    reply = exception2error(sp.create_group, common_name, {})
    group_id = reply.group_id
    # TODO: check reply for error before next step
    # add members to group
    # TODO: do I need to make a separate sp for each request???
    sp = util.get_rpc_stub(request)
    reply = exception2error(sp.add_group_members, [group_id, members], {
            PBException.NOT_FOUND:
                _("Sorry, members of group '" + common_name + "' could not be added because " +
                    "one of the provided member email addresses has no user in " +
                    "this organization associated with it."),
            PBException.WRONG_ORGANIZATION:
                _("Sorry, cannot add an external member to organization group.")
        }
    )
    return {
        'id': group_id
    }


@view_config(
    route_name = 'json.edit_org_group',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_edit_org_group(request):
    _ = request.translate
    sp = util.get_rpc_stub(request)
    group_id = request.json_body['id']
    common_name = request.json_body['name']
    members = request.json_body['members']

    exception2error(sp.set_group_common_name, [group_id, common_name], {})

    current_members = [member['email'] for member in _get_members(request, group_id)]
    to_add = list(set(members) - set(current_members))
    to_remove = list(set(current_members) - set(members))

    if len(to_add) > 0:
        sp = util.get_rpc_stub(request)
        exception2error(sp.add_group_members, [group_id, to_add], {
            PBException.NOT_FOUND:
                _("Sorry, members of group '" + common_name + "' could not be added because " +
                    "one of the provided member email addresses has no user in " +
                    "this organization associated with it."),
            PBException.WRONG_ORGANIZATION:
                _("External users cannot be added to groups. " +
                  "Please remove the following email addresses and retry:")
            }
        )
    if len(to_remove) > 0:
        sp = util.get_rpc_stub(request)
        exception2error(sp.remove_group_members, [group_id, to_remove], {
            PBException.WRONG_ORGANIZATION:
                _("Sorry, cannot add member to organization.")})
    return HTTPOk()


@view_config(
    route_name = 'json.remove_org_group',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_remove_org_group(request):
    sp = util.get_rpc_stub(request)
    group_id = request.json_body['id']

    sp.delete_group(group_id)
    return HTTPOk()

@view_config(
    route_name = 'json.sync_groups',
    permission = 'admin',
    request_method = 'POST'
)
def json_sync_groups(request):
    sp = util.get_rpc_stub(request)
    sp.sync_groups_with_ldap_endpoint()
    return HTTPOk()


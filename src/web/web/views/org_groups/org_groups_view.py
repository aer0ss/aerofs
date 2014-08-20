import logging
from pyramid.view import view_config
# from aerofs_sp.gen.common_pb2 import PBException
from web.sp_util import exception2error
from web import util

log = logging.getLogger(__name__)


@view_config(
    route_name = 'org_groups',
    renderer = 'groups.mako',
    permission = 'admin'
)
def org_groups(request):
    sp = util.get_rpc_stub(request)
    reply = sp.get_org_preferences()

    return {
        'organization_name': reply.organization_name
    }

@view_config(
    route_name = 'json.list_org_groups',
    renderer = 'json',
    permission = 'admin',
    request_method = 'GET'
)
def json_list_org_groups(request):
    sp = util.get_rpc_stub(request)
    # groups = sp.list_groups(20,0,'')
    groups = [];
    return {
        'groups': groups
    }

@view_config(
    route_name = 'json.add_org_group',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_add_org_group(request):
    sp = util.get_rpc_stub(request)
    name = request.json_body['name']
    members = request.json_body['members']

    # make group
    reply = exception2error(sp.create_group, name, {})
    # TODO: check reply for error before next step
    # add members to group
    reply = exception2error(sp.add_group_members, members, {})

    return reply

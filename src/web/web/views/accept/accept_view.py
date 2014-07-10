import logging
import base64

from pyramid.httpexceptions import HTTPOk
from pyramid.view import view_config

from aerofs_sp.gen.common_pb2 import PBException
from web.auth import is_admin
from web.sp_util import exception2error
from web.util import flash_success, get_rpc_stub, is_team_server_user_id
from web.views.payment import stripe_util
from web.views.shared_folders.shared_folders_view import _decode_store_id


log = logging.getLogger(__name__)

URL_PARAM_ORG_ID = 'org_id'
URL_PARAM_SHARE_ID = 'sid'
URL_PARAM_JOINED_TEAM_NAME = 'new_team'

@view_config(
    route_name='accept',
    permission='user',
    renderer='accept.mako'
)
def accept(request):
    return _accept_page_template_variables(request)

@view_config(
    route_name='accept_team_invitation_done',
    permission='user',
    renderer='accept.mako'
)
def accept_team_invitation_done(request):
    _ = request.translate
    team_name = request.params[URL_PARAM_JOINED_TEAM_NAME]
    flash_success(request, _("You are now a member of ${team}", {'team': team_name}))
    return _accept_page_template_variables(request)

def _accept_page_template_variables(request):
    return {
        'team_invitations': get_organization_invitations(request),
        'folder_invitations': get_folder_invitations(request),
        'url_param_org_id': URL_PARAM_ORG_ID,
        'url_param_share_id': URL_PARAM_SHARE_ID,
        'url_param_joined_team_name': URL_PARAM_JOINED_TEAM_NAME,
        'i_am_admin': is_admin(request)
    }

def get_organization_invitations(request):
    sp = get_rpc_stub(request)
    reply = sp.get_organization_invitations()
    results = []

    # This is how we're doing the search and offset/max selection.
    for invite in reply.organization_invitations:
        results.append({
            'inviter': invite.inviter,
            'organization_name': invite.organization_name,
            'organization_id': invite.organization_id
        })

    return results

def get_folder_invitations(request):
    sp = get_rpc_stub(request)
    reply = sp.list_pending_folder_invitations()
    results = []

    for invite in reply.invitation:

        results.append({
            'share_id': invite.share_id.encode('hex'),
            'folder_name': invite.folder_name,
            'sharer': invite.sharer,
            'from_team_server': is_team_server_user_id(invite.sharer)
        })

    return results

@view_config(
    route_name = 'json.accept_team_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def accept_team_invitation(request):
    _ = request.translate

    org_id = int(request.params[URL_PARAM_ORG_ID])

    sp = get_rpc_stub(request)
    reply = exception2error(sp.accept_organization_invitation, org_id, {
        PBException.NO_ADMIN_OR_OWNER: _("no admin for the organization")
    })

    # downgrade subscription for the user's previous org
    stripe_util.update_stripe_subscription(reply.stripe_data)

    return HTTPOk()

@view_config(
    route_name = 'json.ignore_team_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def ignore_team_invitation(request):
    org_id = int(request.params[URL_PARAM_ORG_ID])
    sp = get_rpc_stub(request)
    stripe_data = sp.delete_organization_invitation(org_id).stripe_data
    stripe_util.update_stripe_subscription(stripe_data)

@view_config(
    route_name = 'json.accept_folder_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def accept_folder_invitation(request):
    try:
        share_id = request.params[URL_PARAM_SHARE_ID].decode('hex')
    except KeyError:
        share_id = _decode_store_id(request.json_body['sid'])
    sp = get_rpc_stub(request)
    sp.join_shared_folder(share_id, None)

@view_config(
    route_name = 'json.ignore_folder_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def ignore_folder_invitation(request):
    share_id = request.params[URL_PARAM_SHARE_ID].decode('hex')
    sp = get_rpc_stub(request)

    exception2error(sp.ignore_shared_folder_invitation, share_id, {
        PBException.NO_ADMIN_OR_OWNER:
            "Sorry, you cannot leave the shared folder since you are the only owner "
            "(we are working hard to improve this part)"
    })

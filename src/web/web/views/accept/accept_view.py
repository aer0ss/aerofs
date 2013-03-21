"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging
from pyramid.view import view_config
from web.util import *
from web.views.payment import stripe_util

log = logging.getLogger("web")

@view_config(
    route_name='accept',
    permission='user',
    renderer='accept.mako'
)
def accept(request):
    _ = request.translate

    return {
        'team_invitations': get_organization_invitations(request),
        'folder_invitations': get_folder_invitations(request),
        'success': True}

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
            'sharer': invite.sharer
        })

    return results

@view_config(
    route_name = 'json.accept_organization_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def accept_organization_invitation(request):
    _ = request.translate

    organization_name = request.params['orgname']
    organization_id = request.params['id']

    log.info("org id " + organization_id + " org name " + organization_name)

    sp = get_rpc_stub(request)

    try:
        sp.accept_organization_invitation(int(organization_id))

        reload_auth_level(request)

        msg = _("You are now a member of \"${organization_name}\".",
                {'organization_name': organization_name})

        return {'response_message': msg, 'success': True}
    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

@view_config(
    route_name = 'json.ignore_organization_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def ignore_organization_invitation(request):
    _ = request.translate

    organization_id = int(request.params['id'])

    sp = get_rpc_stub(request)

    try:
        stripe_data = sp.delete_organization_invitation(organization_id)\
                .stripe_data

        stripe_util.downgrade_stripe_subscription(stripe_data)

        msg = _("The invitation has been ignored.")

        return {'response_message': msg, 'success': True}
    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

@view_config(
    route_name = 'json.accept_folder_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def accept_folder_invitation(request):
    _ = request.translate

    share_id = request.params['id'].decode('hex')
    folder_name = request.params['foldername']

    sp = get_rpc_stub(request)

    try:
        sp.join_shared_folder(share_id)
        msg = _("You are now a member of the \"${folder_name}\" shared folder.",
                {'folder_name': folder_name})

        return {'response_message': msg, 'success': True}
    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

@view_config(
    route_name = 'json.ignore_folder_invitation',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def ignore_folder_invitation(request):
    _ = request.translate

    share_id = request.params['id'].decode('hex')
    folder_name = request.params['foldername']

    sp = get_rpc_stub(request)

    try:
        sp.ignore_shared_folder_invitation(share_id)
        msg = _("Invitation to \"${folder_name}\" has been ignored.",
                {'folder_name': folder_name})

        return {'response_message': msg, 'success': True}
    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

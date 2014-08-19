"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging
import base64
import json
from cgi import escape
from pyramid.security import authenticated_userid
from pyramid.view import view_config

import aerofs_sp.gen.common_pb2 as common
from web.auth import is_admin
from web.sp_util import exception2error
from web.util import get_rpc_stub, parse_rpc_error_exception, is_restricted_external_sharing_enabled
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from web import util
from aerofs_sp.gen.common_pb2 import PBException, WRITE, MANAGE
from aerofs_sp.gen.sp_pb2 import JOINED, PENDING, LEFT


log = logging.getLogger(__name__)


def _encode_store_id(sid):
    return base64.b32encode(sid)


def _decode_store_id(encoded_sid):
    return base64.b32decode(encoded_sid)

PAGE_LIMIT = 20


@view_config(
    route_name = 'my_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'user'
)
def my_shared_folders(request):
    _ = request.translate

    return _shared_folders(request,
            _("Manage shared folders"),
            request.route_url('json.get_my_shared_folders'))

@view_config(
    route_name = 'user_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def user_shared_folders(request):
    _ = request.translate
    full_name = request.params[URL_PARAM_FULL_NAME]
    email = request.params[URL_PARAM_USER]

    return _shared_folders(request,
            _("${name}'s shared folders", {'name': full_name}),
            request.route_url('json.get_user_shared_folders', _query={
                URL_PARAM_USER: email
                }))


@view_config(
    route_name = 'org_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def org_shared_folders(request):
    _ = request.translate

    return _shared_folders(request,
            _("Shared folders in my organization"),
            request.route_url('json.get_org_shared_folders'),
            has_pagination=True)


# @param {un,}privileged_modal_subtitle: the subtitles used in the shared folder
# modal if the user {has,doesn't have) the privilege to manage the folder.
def _shared_folders(request, page_heading, data_url, has_pagination=False):
    return {
        'pagination_limit': PAGE_LIMIT,
        # variables
        'session_user': authenticated_userid(request),
        'is_admin': is_admin(request),
        'use_restricted': is_restricted_external_sharing_enabled(request.registry.settings),
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'has_pagination': has_pagination,
        'data_url': data_url,
        'splash': util.show_welcome_image_and_set_cookie(request)
    }


@view_config(
    route_name = 'json.get_my_shared_folders',
    renderer = 'json',
    permission = 'user'
)
def json_get_my_shared_folders(request):
    return json_get_user_shared_folders(request, is_me=True)


def _is_joined(folder, user):
    """ return true if the user is a JOINED member of the PBSharedFolder """
    return any(ups.user.user_email == user and ups.state == JOINED for ups in folder.user_permissions_and_state)

def _is_left(folder, user):
    """ return true if the user is a LEFT member of the PBSharedFolder """
    return any(ups.user.user_email == user and ups.state == LEFT for ups in folder.user_permissions_and_state)


def _session_user_privileger(folder, session_user):
    return _has_permission(folder, session_user, common.MANAGE)


def _has_permission(folder, user, permission):
    for user_permissions_and_state in folder.user_permissions_and_state:
        if user_permissions_and_state.user.user_email == user:
            for p in user_permissions_and_state.permissions.permission:
                if p == permission:
                    return True
            break
    return False


@view_config(
    route_name = 'json.get_user_shared_folders',
    renderer = 'json',
    permission = 'user'
)
def json_get_user_shared_folders(request, is_me=False):
    if is_me:
        specified_user = authenticated_userid(request)
    else:
        specified_user = request.params[URL_PARAM_USER]

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    reply = sp.list_user_shared_folders(specified_user)
    folders = [f for f in reply.shared_folder if (_is_joined(f, specified_user) or _is_left(f, specified_user))]
    return _sp_reply2json(folders,
        _session_team_privileger, authenticated_userid(request), request, is_mine=is_me, specified_user=specified_user)


@view_config(
    route_name = 'json.get_org_shared_folders',
    renderer = 'json',
    permission = 'admin'
)
def json_get_org_shared_folders(request):
    try:
        count = int(request.params['count'])
    except KeyError:
        count = PAGE_LIMIT

    try:
        offset = int(request.params['offset'])
    except KeyError:
        offset = 0
    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    reply = sp.list_organization_shared_folders(count, offset)
    return _sp_reply2json(reply.shared_folder, _session_team_privileger,
        authenticated_userid(request), request, total=reply.total_count, offset=offset)


def _session_team_privileger(folder, session_user):
    return folder.owned_by_team

def _jsonable_person(person):
    """ Converts protobuf user object to dictionary """
    is_owner = False
    can_edit = False
    if person.state == PENDING:
        is_pending = True
    else:
        is_pending = False
    if WRITE in person.permissions.permission:
        can_edit = True
    if MANAGE in person.permissions.permission:
        is_owner = True
    return {
        'first_name': person.user.first_name,
        'last_name': person.user.last_name,
        'is_owner': is_owner,
        'can_edit': can_edit,
        'is_pending': is_pending,
        'email': person.user.user_email
    }

def _jsonable_people(people_list, session_user):
    """ Converts list of protobuf user objects to a list of dictionaries 
    that json can handle, then moves "me" to end of the list, if necessary"""
    json_people = (_jsonable_person(p) for p in people_list)
    return sorted(json_people, key=lambda x: x.get('email') == session_user)


def _sp_reply2json(folders, privileger, session_user, request, total=None, offset=0, is_mine=False, specified_user=None):
    """
    @param privileger a callback function to determine if the session user has
        privileges to modify ACL of the folder
    """
    user = specified_user or session_user
    data = []
    for folder in folders:
        # a workaround to filter folder.user_permissions_and_state into owners and members
        # due to Protobuf magic, you can't just use folder twice
        member_folder = folder
        owners = filter(lambda urs: urs.state in (JOINED, LEFT, PENDING) and MANAGE in urs.permissions.permission, 
            folder.user_permissions_and_state)
        members = filter(lambda urs: urs.state in (JOINED, LEFT, PENDING) and MANAGE not in urs.permissions.permission, 
            member_folder.user_permissions_and_state)

        id = _encode_store_id(folder.store_id)
        reordered_owners_list = _jsonable_people(list(owners), session_user)
        reordered_members_list = _jsonable_people(list(members), session_user)
        data.append({
            'name': escape(folder.name),
            'owners': reordered_owners_list,
            'members': reordered_members_list,
            'sid': escape(id),
            'is_privileged': 1 if privileger(folder, session_user) else 0,
            'is_member': is_mine,
            'is_left': _is_left(folder, user)
        })

    if not total:
        total = len(folders)

    return {
        'data': data,
        'me': session_user,
        'total': total,
        'offset': offset
    }


def to_json(user_permissions_and_state_list, session_user):
    # TODO: REST-ify SP and all this mess will go away...
    # json.dumps() can't convert the object so we have to do it manually.
    urss = {}
    for urs in user_permissions_and_state_list:
        urss[urs.user.user_email] = {
            "first_name":   _get_first_name(urs, session_user),
            "last_name":    _get_last_name(urs, session_user),
            "permissions":  _pb_permissions_to_json(urs.permissions),
            "state": urs.state
        }

    # dump to a compact-format JSON string
    return json.dumps(urss, separators=(',',':'))


def _get_first_name(user_permissions_and_state, session_user):
    """
    @param user_permissions_and_state a PBUserPermissionsAndState object
    """
    user = user_permissions_and_state.user
    return "me" if user.user_email == session_user else user.first_name


def _get_last_name(user_permissions_and_state, session_user):
    """
    otherwise.
    @param user_permissions_and_state a PBUserPermissionsAndState object
    """
    user = user_permissions_and_state.user
    return "" if user.user_email == session_user else user.last_name


def _pb_permissions_to_json(pb):
    permissions = []
    for p in pb.permission:
        permissions.append(common.PBPermission.Name(p))
    return permissions


def _pb_permissions_from_json(permission_list):
    pb = common.PBPermissions()
    for p in permission_list:
        pb.permission.append(common.PBPermission.Value(p))
    return pb


@view_config(
    route_name = 'json.leave_shared_folder',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_leave_shared_folder(request):
    store_id = _decode_store_id(request.json_body['store_id'])
    sp = get_rpc_stub(request)
    exception2error(sp.leave_shared_folder, store_id, {})


@view_config(
    route_name = 'json.destroy_shared_folder',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_destroy_shared_folder(request):
    store_id = _decode_store_id(request.json_body['store_id'])
    sp = get_rpc_stub(request)
    exception2error(sp.destroy_shared_folder, store_id, {})


@view_config(
    route_name = 'json.add_shared_folder_perm',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_add_shared_folder_perm(request):
    """
    add_folder_perm invites a user to the given shared folder.
    It adds permissions for them to the ACL table and handles inviting
    users who aren't yet part of AeroFS. The major difference between
    it and set_folder_perm is that it sends an email invitation to the
    invited user, while set_folder_perm assumes the given user already has
    permissions for the given folder
    """
    _ = request.translate

    user_id = request.json_body['user_id']
    store_id = _decode_store_id(request.json_body['store_id'])
    folder_name = request.json_body['folder_name']
    permissions = request.json_body['permissions']
    suppress_warnings = request.json_body['suppress_sharing_rules_warnings']

    subject_permissions = common.PBSubjectPermissions()
    subject_permissions.subject = user_id

    # stupid fscking moronic Googlers and their dumb fscking "optimizations"
    #   1. you can't just set a nested message field, that would be too simple
    #   2. accessing a nested message is not enough, you need to modify one of its fields
    #   3. RepeatedScalarFieldContainer.extend is a noop for empty lists
    #
    # Hence the need for this redundant insert/remove, otherwise trying to share with an empty
    # permission list (i.e. VIEWER role) results in the permissions field being missing and
    # the serializer throws a fit because that's a required field.
    #
    # TLDR: the sooner we make SP RESTful the better.
    subject_permissions.permissions.permission.append(common.WRITE)
    subject_permissions.permissions.permission.remove(common.WRITE)

    subject_permissions.permissions.permission.extend([common.PBPermission.Value(p) for p in permissions])

    sp = get_rpc_stub(request)

    exception2error(sp.share_folder, (folder_name, store_id, [subject_permissions], None,
                                      None, suppress_warnings, []),
                                      _add_shared_folder_rules_errors({
        # TODO (WW) change to ALREADY_MEMBER?
        # See also org_users_view.py:json_invite_user()
        PBException.ALREADY_EXIST:
            _("The user is already a member of the folder."),
        PBException.EMPTY_EMAIL_ADDRESS:
            _("The email address can't be empty"),
        PBException.NO_PERM:
            _("You don't have permission to invite people to this folder"),
    }))


def _add_shared_folder_rules_errors(dict):
    """
    Add errors thrown by shared folder rules to the dictionary parameter and
    return the result.
    """
    # These exceptions are handled by JavaScript, and the messages are ignored
    dict.update({
        PBException.SHARING_RULES_ERROR: "",
        PBException.SHARING_RULES_WARNINGS: "",
    })
    return dict


@view_config(
    route_name = 'json.set_shared_folder_perm',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_set_shared_folder_perm(request):
    """
    This method modifies permissions that a user already has for
    a shared folder through the update_acl call. Though it can also create
    permissions for a user that don't exist beforehand, it isn't meant to
    be used in this way (json.add_folder_perm should be used in that case
    because it also handles inviting the user if they aren't part of AeroFS
    and sends them a notification email about the new shared folder)
    """
    _ = request.translate

    storeid = _decode_store_id(request.json_body['store_id'])
    userid = request.json_body['user_id']
    permissions = _pb_permissions_from_json(request.json_body['permissions'])
    suppress_warnings = request.json_body['suppress_sharing_rules_warnings']

    sp = get_rpc_stub(request)

    exception2error(sp.update_acl, (storeid, userid, permissions, suppress_warnings),
        _add_shared_folder_rules_errors({
            PBException.NO_PERM: _("You don't have permission to change roles"),
        }))

@view_config(
    route_name = 'json.delete_shared_folder_perm',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_delete_shared_folder_perm(request):
    storeid = _decode_store_id(request.json_body['store_id'])
    userid = request.json_body['user_id']

    sp = get_rpc_stub(request)
    try:
        sp.delete_acl(storeid, userid)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

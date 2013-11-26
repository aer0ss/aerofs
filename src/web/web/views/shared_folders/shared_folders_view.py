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
from web.util import get_rpc_stub, parse_rpc_error_exception
from ..org_users.org_users_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from web import util
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.gen.sp_pb2 import JOINED

from web.views.payment.stripe_util\
    import URL_PARAM_STRIPE_CARD_TOKEN, STRIPE_PUBLISHABLE_KEY


log = logging.getLogger(__name__)


def _encode_store_id(sid):
    return base64.b32encode(sid)


def _decode_store_id(encoded_sid):
    return base64.b32decode(encoded_sid)

# HTML class for links that open the Options modal
_OPEN_MODAL_CLASS = 'open-modal'

# HTML data attributes used for links that opens the shared folder Options modal
_LINK_DATA_SID = 's'
_LINK_DATA_NAME = 'n'
_LINK_DATA_PRIVILEGED = 'p'
_LINK_DATA_USER_PERMISSIONS_AND_STATE_LIST = 'r'

class DatatablesPaginate:
    YES, NO = range(2)

@view_config(
    route_name = 'dashboard_home',
    renderer = 'shared_folders.mako',
    permission = 'user'
)
@view_config(
    route_name = 'my_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'user'
)
def my_shared_folders(request):
    _ = request.translate

    return _shared_folders(DatatablesPaginate.NO, request,
            _("My shared folders"),
            request.route_url('json.get_my_shared_folders'),
            _("You can manage this folder because you are an Owner of this"
              " folder."),
            _("You cannot change this folder's settings because you are not"
              " an Owner of this folder."))

_PRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN = \
    "You can manage this folder because you are an organization admin and one or more " \
    "users in your organization are this folder's Owners."
_UNPRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN = \
    "You cannot change this folder's settings since no users in your " \
    "organization are its Owners."

@view_config(
    route_name = 'user_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def user_shared_folders(request):
    _ = request.translate
    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]

    return _shared_folders(DatatablesPaginate.NO, request,
            _("${name}'s shared folders", {'name': full_name}),
            request.route_url('json.get_user_shared_folders', _query={URL_PARAM_USER: user}),
            _(_PRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN),
            _(_UNPRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN))


@view_config(
    route_name = 'org_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def org_shared_folders(request):
    _ = request.translate

    return _shared_folders(DatatablesPaginate.YES, request,
            _("Shared folders in my organization"),
            request.route_url('json.get_org_shared_folders'),
            _(_PRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN),
            _(_UNPRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN))


# @param {un,}privileged_modal_subtitle: the subtitles used in the shared folder
# modal if the user {has,doesn't have) the privilege to manage the folder.
def _shared_folders(datatables_paginate, request,
                    page_heading, datatables_request_route_url,
                    privileged_modal_tooltip,
                    unprivileged_modal_tooltip):
    return {
        # constants
        'open_modal_class': _OPEN_MODAL_CLASS,
        'link_data_sid': _LINK_DATA_SID,
        'link_data_name': _LINK_DATA_NAME,
        'link_data_privileged': _LINK_DATA_PRIVILEGED,
        'link_data_user_permissions_and_state_list': _LINK_DATA_USER_PERMISSIONS_AND_STATE_LIST,
        'stripe_publishable_key': STRIPE_PUBLISHABLE_KEY,
        'url_param_stripe_card_token': URL_PARAM_STRIPE_CARD_TOKEN,

        # variables
        'session_user': authenticated_userid(request),
        'is_admin': is_admin(request),
        'datatables_paginate': datatables_paginate == DatatablesPaginate.YES,
        # N.B. can't use "page_title" as the variable name. base_layout.mako
        # defines a global variable using the same name.
        'page_heading': page_heading,
        'datatables_request_route_url': datatables_request_route_url,
        'privileged_modal_tooltip': privileged_modal_tooltip,
        'unprivileged_modal_tooltip': unprivileged_modal_tooltip
    }


@view_config(
    route_name = 'json.get_my_shared_folders',
    renderer = 'json',
    permission = 'user'
)
def json_get_my_shared_folders(request):
    echo = request.params['sEcho']

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    session_user = authenticated_userid(request)
    reply = sp.list_user_shared_folders(session_user)
    return _sp_reply2datatables(reply.shared_folder,
        _session_user_privileger,
        len(reply.shared_folder), echo, session_user)


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
def json_get_user_shared_folders(request):
    echo = request.params['sEcho']
    specified_user = request.params[URL_PARAM_USER]

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    reply = sp.list_user_shared_folders(specified_user)
    return _sp_reply2datatables(reply.shared_folder,
        _session_team_privileger,
        len(reply.shared_folder), echo, authenticated_userid(request))


@view_config(
    route_name = 'json.get_org_shared_folders',
    renderer = 'json',
    permission = 'admin'
)
def json_get_org_shared_folders(request):
    echo = request.params['sEcho']
    count = int(request.params['iDisplayLength'])
    offset = int(request.params['iDisplayStart'])

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    reply = sp.list_organization_shared_folders(count, offset)
    return _sp_reply2datatables(reply.shared_folder, _session_team_privileger,
        reply.total_count, echo, authenticated_userid(request))


def _session_team_privileger(folder, session_user):
    return folder.owned_by_team


def _sp_reply2datatables(folders, privileger, total_count, echo, session_user):
    """
    @param privileger a callback function to determine if the session user has
        privileges to modify ACL of the folder
    """
    data = []
    for folder in folders:
        # a workaround to filter folder.user_permissions_and_state to leave only joined users
        # using del & extend because setting folder.user_permissions_and_state doesn't work
        # due to Protobuf magic
        filtered_urss = filter(lambda urs: urs.state == JOINED, folder.user_permissions_and_state)
        del folder.user_permissions_and_state[:]
        folder.user_permissions_and_state.extend(filtered_urss)

        data.append({
            'name': escape(folder.name),
            'users': _render_shared_folder_users(folder.user_permissions_and_state, session_user),
            'options': _render_shared_folder_options_link(folder, session_user,
                privileger(folder, session_user)),
        })

    return {
        'sEcho': echo,
        'iTotalRecords': total_count,
        'iTotalDisplayRecords': total_count,
        'aaData': data
    }


def _render_shared_folder_users(user_permissions_and_state_list, session_user):

    # Place 'me' to the end of the list so that if the list is too long we show
    # "Foo, Bar, and 10 others" instead of "Foo, me, and 10 others"
    #
    # Also, it's polite to place "me" last.
    #
    reordered_list = list(user_permissions_and_state_list)
    for i in range(len(reordered_list)):
        if reordered_list[i].user.user_email == session_user:
            myself = reordered_list.pop(i)
            reordered_list.append(myself)
            break

    total = len(reordered_list)

    str = ''
    if total == 0:
        pass
    elif total == 1:
        str = _get_first_name(reordered_list[0], session_user) + " only"
    elif total == 2:
        str = u"{} and {}".format(
            _get_first_name(reordered_list[0], session_user),
            _get_first_name(reordered_list[1], session_user))
    elif total < 5:
        for i in range(total):
            if i > 0: str += ", "
            if i == total - 1: str += "and "
            str += _get_first_name(reordered_list[i], session_user)
    else:
        # If there are more than 5 people, print the first 3 only
        printed = 3
        for i in range(printed):
            str += _get_first_name(reordered_list[i], session_user)
            str += ", "
        str += "and {} others".format(total - printed)

    return escape(str)


def _render_shared_folder_options_link(folder, session_user, privileged):
    """
    @param privileged whether the session user has the privilege to modify ACL
    """
    id = _encode_store_id(folder.store_id)
    urs = to_json(folder.user_permissions_and_state, session_user)
    escaped_folder_name = escape(folder.name)

    # The data tags must be consistent with the ones in loadModalData() in
    # shared_folder.mako.
    # use single quote for data-srps since srps is in JSON which uses double
    # quotes.
    #
    # N.B. need to sub out quotes for proper rendering of the dialog.
    return u'<a href="#" class="{}" data-{}="{}" data-{}="{}"' \
           u'data-{}="{}" data-{}="{}">{}</a>'.format(
            _OPEN_MODAL_CLASS,
            _LINK_DATA_SID, escape(id),
            _LINK_DATA_PRIVILEGED, 1 if privileged else 0,
            _LINK_DATA_NAME, escaped_folder_name,
            _LINK_DATA_USER_PERMISSIONS_AND_STATE_LIST, escape(urs).replace('"', '&#34;'),
            "Manage Folder" if privileged else "View Members")


def to_json(user_permissions_and_state_list, session_user):
    # TODO: REST-ify SP and all this mess will go away...
    # json.dumps() can't convert the object so we have to do it manually.
    urss = {}
    for urs in user_permissions_and_state_list:
        urss[urs.user.user_email] = {
            "first_name":   _get_first_name(urs, session_user),
            "last_name":    _get_last_name(urs, session_user),
            "permissions":  _pb_permissions_to_json(urs.permissions)
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
                                      None, suppress_warnings),
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
    _ = request.translate

    storeid = _decode_store_id(request.json_body['store_id'])
    userid = request.json_body['user_id']

    sp = get_rpc_stub(request)
    try:
        sp.delete_acl(storeid, userid)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

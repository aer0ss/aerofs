"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging
import base64
import json
from cgi import escape

from pyramid.view import view_config

import aerofs_sp.gen.common_pb2 as common
from web.auth import get_session_user, is_admin
from web.sp_util import exception2error
from web.util import get_rpc_stub, parse_rpc_error_exception
from ..team_members.team_members_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from web import util
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.gen.sp_pb2 import JOINED

from web.views.payment.stripe_util\
    import URL_PARAM_STRIPE_CARD_TOKEN, STRIPE_PUBLISHABLE_KEY


log = logging.getLogger("web")


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
_LINK_DATA_USER_ROLE_AND_STATE_LIST = 'r'

# JSON keys
_USER_ROLE_AND_STATE_FIRST_NAME_KEY = 'first_name'
_USER_ROLE_AND_STATE_LAST_NAME_KEY = 'last_name'
_USER_ROLE_AND_STATE_ROLE_KEY = 'role'

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
            _("My Shared Folders"),
            request.route_url('json.get_my_shared_folders'),
            _("You can manage this folder because you are an Owner of this"
              " folder."),
            _("You cannot change this folder's settings because you are not"
              " an Owner of this folder."))

_PRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN = \
    "You can manage this folder because you are a team admin and one or more " \
    "teammates in your team are this folder's Owners."
_UNPRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN = \
    "You cannot change this folder's settings since no teammates in your " \
    "team are its Owners."

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
            _("${name}'s Shared Folders", {'name': full_name}),
            request.route_url('json.get_user_shared_folders', _query={URL_PARAM_USER: user}),
            _(_PRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN),
            _(_UNPRIVILEGED_MODEL_TOOLTIP_FOR_TEAM_ADMIN))


@view_config(
    route_name = 'team_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def team_shared_folders(request):
    _ = request.translate

    return _shared_folders(DatatablesPaginate.YES, request,
            _("Team's Shared Folders"),
            request.route_url('json.get_team_shared_folders'),
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
        'link_data_user_role_and_state_list': _LINK_DATA_USER_ROLE_AND_STATE_LIST,
        'user_role_and_state_first_name_key': _USER_ROLE_AND_STATE_FIRST_NAME_KEY,
        'user_role_and_state_last_name_key': _USER_ROLE_AND_STATE_LAST_NAME_KEY,
        'user_role_and_state_role_key': _USER_ROLE_AND_STATE_ROLE_KEY,
        'stripe_publishable_key': STRIPE_PUBLISHABLE_KEY,
        'url_param_stripe_card_token': URL_PARAM_STRIPE_CARD_TOKEN,
        'owner_role': common.OWNER,
        'editor_role': common.EDITOR,
        'viewer_role': common.VIEWER,

        # variables
        'session_user': get_session_user(request),
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
    session_user = get_session_user(request)
    reply = sp.list_user_shared_folders(session_user)
    return _sp_reply2datatables(reply.shared_folder,
        _session_user_privileger,
        len(reply.shared_folder), echo, session_user)


def _session_user_privileger(folder, session_user):
    return _get_role(folder, session_user) == common.OWNER

def _get_role(folder, user):
    for user_role_and_state in folder.user_role_and_state:
        if user_role_and_state.user.user_email == user:
            return user_role_and_state.role


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
        len(reply.shared_folder), echo, get_session_user(request))


@view_config(
    route_name = 'json.get_team_shared_folders',
    renderer = 'json',
    permission = 'admin'
)
def json_get_team_shared_folders(request):
    echo = request.params['sEcho']
    count = int(request.params['iDisplayLength'])
    offset = int(request.params['iDisplayStart'])

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = util.get_rpc_stub(request)
    reply = sp.list_organization_shared_folders(count, offset)
    return _sp_reply2datatables(reply.shared_folder, _session_team_privileger,
        reply.total_count, echo, get_session_user(request))


def _session_team_privileger(folder, session_user):
    return folder.owned_by_team


def _sp_reply2datatables(folders, privileger, total_count, echo, session_user):
    """
    @param privileger a callback function to determine if the session user has
        privileges to modify ACL of the folder
    """
    data = []
    for folder in folders:
        # a workaround to filter folder.user_role_and_state to leave only joined users
        # using del & extend because setting folder.user_role_and_state doesn't work
        # due to Protobuf magic
        filtered_urss = filter(lambda urs: urs.state == JOINED, folder.user_role_and_state)
        del folder.user_role_and_state[:]
        folder.user_role_and_state.extend(filtered_urss)

        data.append({
            'name': escape(folder.name),
            'users': _render_shared_folder_users(folder.user_role_and_state, session_user),
            'options': _render_shared_folder_options_link(folder, session_user,
                privileger(folder, session_user)),
        })

    return {
        'sEcho': echo,
        'iTotalRecords': total_count,
        'iTotalDisplayRecords': total_count,
        'aaData': data
    }


def _render_shared_folder_users(user_role_and_state_list, session_user):

    # Place 'me' to the end of the list so that if the list is too long we show
    # "Foo, Bar, and 10 others" instead of "Foo, me, and 10 others"
    #
    # Also, it's polite to place "me" last.
    #
    reordered_list = list(user_role_and_state_list)
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
    urs = to_json(folder.user_role_and_state, session_user)
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
            _LINK_DATA_USER_ROLE_AND_STATE_LIST, escape(urs).replace('"', '&#34;'),
            "Manage Folder" if privileged else "View Members")


def to_json(user_role_and_state_list, session_user):

    # json.dumps() can't convert the object so we have to do it manually.
    urss = {}
    for urs in user_role_and_state_list:
        urss[urs.user.user_email] = {
            _USER_ROLE_AND_STATE_FIRST_NAME_KEY:  _get_first_name(urs, session_user),
            _USER_ROLE_AND_STATE_LAST_NAME_KEY:   _get_last_name(urs, session_user),
            _USER_ROLE_AND_STATE_ROLE_KEY:        urs.role
        }

    # dump to a compact-format JSON string
    return json.dumps(urss, separators=(',',':'))


def _get_first_name(user_role_and_state, session_user):
    """
    @param user_role_and_state a PBUserAndRole object
    """
    user = user_role_and_state.user
    return "me" if user.user_email == session_user else user.first_name


def _get_last_name(user_role_and_state, session_user):
    """
    otherwise.
    @param user_role_and_state a PBUserAndRole object
    """
    user = user_role_and_state.user
    return "" if user.user_email == session_user else user.last_name


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

    user_id = request.params['user_id']
    store_id = _decode_store_id(request.params['store_id'])
    folder_name = request.params['folder_name']
    role = int(request.params['role'])
    suppress_warnings = request.params['suppress_shared_folders_rules_warnings'] == 'true'

    role_pair = common.PBSubjectRolePair()
    role_pair.subject = user_id
    role_pair.role = role

    sp = get_rpc_stub(request)

    exception2error(sp.share_folder, (folder_name, store_id, [role_pair], None,
                                      None, suppress_warnings),
                                      _add_shared_folder_rules_errors({
        # TODO (WW) change to ALREADY_MEMBER?
        # See also team_members_view.py:json_invite_user()
        PBException.ALREADY_EXIST:
            _("The user is already a member of the folder."),
        PBException.EMPTY_EMAIL_ADDRESS:
            _("The email address can't be empty"),
        PBException.NO_STRIPE_CUSTOMER_ID:
            _("Payment is required to invite more collaborators"),
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
        PBException.SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALLY_SHARED_FOLDER: "",
        PBException.SHARED_FOLDER_RULES_WARNING_ADD_EXTERNAL_USER: "",
        PBException.SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS: "",
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

    storeid = _decode_store_id(request.params['store_id'])
    userid = request.params['user_id']
    role = int(request.params['role'])
    suppress_warnings = request.params['suppress_shared_folders_rules_warnings'] == 'true'

    sp = get_rpc_stub(request)

    exception2error(sp.update_acl, (storeid, userid, role, suppress_warnings),
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

    storeid = _decode_store_id(request.params['store_id'])
    userid = request.params['user_id']

    sp = get_rpc_stub(request)
    try:
        sp.delete_acl(storeid, userid)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

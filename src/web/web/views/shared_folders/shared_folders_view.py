"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging, base64, json, urllib
from cgi import escape
from pyramid.view import view_config
import aerofs_sp.gen.common_pb2 as common
from web.sp_util import exception2error
from web.util import *
from ..team_members.team_members_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from web import util
from aerofs_sp.gen.common_pb2 import PBException

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
_LINK_DATA_USER_AND_ROLE_LIST = 'r'

# JSON keys
_USER_AND_ROLE_FIRST_NAME_KEY = 'first_name'
_USER_AND_ROLE_LAST_NAME_KEY = 'last_name'
_USER_AND_ROLE_IS_OWNER_KEY = 'owner'

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

    return _shared_folders(False, request, _("Shared Folders"),
            request.route_url('json.get_my_shared_folders'))

@view_config(
    route_name = 'user_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def user_shared_folders(request):
    _ = request.translate
    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]

    return _shared_folders(False, request,
                           _("${name}'s Shared Folders", {'name': full_name}),
                           request.route_url('json.get_user_shared_folders', _query={URL_PARAM_USER: user}))

@view_config(
    route_name = 'team_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def team_shared_folders(request):
    _ = request.translate

    return _shared_folders(True, request,
            _("Team's Shared Folders"),
            request.route_url('json.get_team_shared_folders'))

def _shared_folders(datatables_paginate, request,
                    page_title, datatables_request_route_url):
    return {
        # constants
        'open_modal_class': _OPEN_MODAL_CLASS,
        'link_data_sid': _LINK_DATA_SID,
        'link_data_name': _LINK_DATA_NAME,
        'link_data_privileged': _LINK_DATA_PRIVILEGED,
        'link_data_user_and_role_list': _LINK_DATA_USER_AND_ROLE_LIST,
        'user_and_role_first_name_key': _USER_AND_ROLE_FIRST_NAME_KEY,
        'user_and_role_last_name_key': _USER_AND_ROLE_LAST_NAME_KEY,
        'user_and_role_is_owner_key': _USER_AND_ROLE_IS_OWNER_KEY,

        # variables
        'session_user': get_session_user(request),
        'is_admin': is_admin(request),
        'datatables_paginate': datatables_paginate,
        'page_title': page_title,
        'datatables_request_route_url': datatables_request_route_url,
        'stripe_publishable_key': STRIPE_PUBLISHABLE_KEY,
        'url_param_stripe_card_token': URL_PARAM_STRIPE_CARD_TOKEN
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
        _session_user_privileger, _session_user_labeler,
        len(reply.shared_folder), echo, session_user)

def _session_user_privileger(folder, session_user):
    for user_and_role in folder.user_and_role:
        if user_and_role.role == common.OWNER and\
           user_and_role.user.user_email == session_user:
            # The label text and style must be consistent with the label
            # generated in shared_folders.mako.
            return True
    return False

def _session_user_labeler(privileged):
    # label text and style must match the labels generated in shared_folder.mako.
    return '<span class="label tooltip_owned_by_me" data-toggle="tooltip">owner</span>' if privileged else ''

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
        _session_team_privileger, _session_team_labeler,
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
    return _sp_reply2datatables(reply.shared_folder,
        _session_team_privileger, _session_team_labeler,
        reply.total_count, echo, get_session_user(request))

def _session_team_privileger(folder, session_user):
    return folder.owned_by_team

def _session_team_labeler(privileged):
    if not privileged: return ''
    # label text and style must match the labels generated in shared_folder.mako.
    return '<span class="label tooltip_owned_by_team" data-toggle="tooltip">owned by team</span>' \
            if privileged else ''

def _sp_reply2datatables(folders, privileger, labeler, total_count, echo, session_user):
    """
    @param privileger a callback function to determine if the session user has
        privileges to modify ACL of the folder
    @param labeler a callback function to render appropriate labels for the
        folder given the privilage
    """
    data = []
    for folder in folders:
        privileged = privileger(folder, session_user)
        data.append({
            'name': escape(folder.name),
            'label': labeler(privileged),
            'users': _render_shared_folder_users(folder.user_and_role,
                session_user),
            'options': _render_shared_folder_options_link(folder, session_user,
                privileged),
        })

    return {
        'sEcho': echo,
        'iTotalRecords': total_count,
        'iTotalDisplayRecords': total_count,
        'aaData': data
    }

def _render_shared_folder_users(user_and_role_list, session_user):

    # Place 'me' to the end of the list so that if the list is too long we show
    # "Foo, Bar, and 10 others" instead of "Foo, me, and 10 others"
    #
    # Also, it's polite to place "me" last.
    #
    reordered_list = list(user_and_role_list)
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
    urs = to_json(folder.user_and_role, session_user)
    escaped_folder_name = escape(folder.name)

    # The data tags must be consistent with the ones in loadModalData() in
    # shared_folder.mako.
    # use single quote for data-srps since srps is in JSON which uses double
    # quotes.
    #
    # N.B. need to sub out quotes for proper rendering of the dialog.
    return u'<a href="#" class="{}" data-{}="{}" data-{}="{}"' \
           u'data-{}="{}" data-{}="{}">Options</a>'.format(
            _OPEN_MODAL_CLASS,
            _LINK_DATA_SID, escape(id),
            _LINK_DATA_PRIVILEGED, 1 if privileged else 0,
            _LINK_DATA_NAME, escaped_folder_name,
            _LINK_DATA_USER_AND_ROLE_LIST, escape(urs).replace('"', '&#34;'))

def to_json(user_and_role_list, session_user):

    # json.dumps() can't convert the object so we have to do it manually.
    urs = {}
    for ur in user_and_role_list:
        urs[ur.user.user_email] = {
            _USER_AND_ROLE_FIRST_NAME_KEY:
                _get_first_name(ur, session_user),
            _USER_AND_ROLE_LAST_NAME_KEY:
                _get_last_name(ur, session_user),
            _USER_AND_ROLE_IS_OWNER_KEY:
                (1 if ur.role == common.OWNER else 0)
        }

    # dump to a compact-format JSON string
    return json.dumps(urs, separators=(',',':'))

def _get_first_name(user_and_role, session_user):
    """
    @param user_and_role a PBUserAndRole object
    """
    user = user_and_role.user
    return "me" if user.user_email == session_user else user.first_name

def _get_last_name(user_and_role, session_user):
    """
    otherwise.
    @param user_and_role a PBUserAndRole object
    """
    user = user_and_role.user
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
    note = request.params.get('note') or ''

    role_pair = common.PBSubjectRolePair()
    role_pair.subject = user_id
    # editor by default when added
    # TODO (WW) why not simply use common.EDITOR?
    role_pair.role = common._PBROLE.values_by_name['EDITOR'].number

    sp = get_rpc_stub(request)
    exception2error(sp.share_folder, (folder_name, store_id, [role_pair], note, None), {
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
    })

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

    storeid = _decode_store_id(request.params['storeid'])
    userid = request.params['userid']
    # TODO (WW) a smarter way to retrieve the role number
    role = common._PBROLE.values_by_name[request.params['perm']].number

    sp = get_rpc_stub(request)
    try:
        sp.update_acl(storeid, userid, role)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

@view_config(
    route_name = 'json.delete_shared_folder_perm',
    renderer = 'json',
    permission = 'user',
    request_method = 'POST'
)
def json_delete_shared_folder_perm(request):
    _ = request.translate

    storeid = _decode_store_id(request.params['storeid'])
    userid = request.params['userid']

    sp = get_rpc_stub(request)
    try:
        sp.delete_acl(storeid, userid)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

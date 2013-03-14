"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging, base64, json, urllib
from cgi import escape
from pyramid.view import view_config
import aerofs_sp.gen.common_pb2 as common
from web.helper_functions import *
from ..admin_panel.admin_panel_view import URL_PARAM_USER, URL_PARAM_FULL_NAME
from web import helper_functions

log = logging.getLogger("web")

def get_permission(pbrole):
    return common._PBROLE.values_by_number[int(pbrole)].name

def encode_store_id(sid):
    return base64.b32encode(sid)

def decode_store_id(encoded_sid):
    return base64.b32decode(encoded_sid)

# HTML class for links that open the Options modal
_OPEN_MODAL_CLASS = 'open-modal'

# HTML data attributes used for links that opens the shared folder Options modal
_LINK_DATA_SID = 's'
_LINK_DATA_NAME = 'n'
_LINK_DATA_USER_AND_ROLE_LIST = 'r'

# JSON keys
_USER_AND_ROLE_FIRST_NAME_KEY = 'first_name'
_USER_AND_ROLE_LAST_NAME_KEY = 'last_name'
_USER_AND_ROLE_IS_OWNER_KEY = 'owner'

@view_config(
    route_name = 'my_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'user'
)
def my_shared_folders(request):
    _ = request.translate

    session_user = get_session_user(request)

    return _shared_folders(False, False, session_user,
            _("Shared Folders"),
            _json_get_user_shared_folders_url(request, session_user))

@view_config(
    route_name = 'user_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def user_shared_folders(request):
    _ = request.translate
    user = request.params[URL_PARAM_USER]
    full_name = request.params[URL_PARAM_FULL_NAME]

    return _shared_folders(False, True, get_session_user(request),
        _("${name}'s Shared Folders", {'name': full_name}),
        _json_get_user_shared_folders_url(request, user))

@view_config(
    route_name = 'organization_shared_folders',
    renderer = 'shared_folders.mako',
    permission = 'admin'
)
def organization_shared_folders(request):
    _ = request.translate

    return _shared_folders(True, True, get_session_user(request),
            _("Team's Shared Folders"),
            request.route_url('json.get_organization_shared_folders'))

def _shared_folders(datatables_paginate, admin_privilege, session_user,
                    page_title, datatables_request_route_url):
    return {
        # constants
        'open_modal_class': _OPEN_MODAL_CLASS,
        'link_data_sid': _LINK_DATA_SID,
        'link_data_name': _LINK_DATA_NAME,
        'link_data_user_and_role_list': _LINK_DATA_USER_AND_ROLE_LIST,
        'user_and_role_first_name_key': _USER_AND_ROLE_FIRST_NAME_KEY,
        'user_and_role_last_name_key': _USER_AND_ROLE_LAST_NAME_KEY,
        'user_and_role_is_owner_key': _USER_AND_ROLE_IS_OWNER_KEY,

        # variables
        'session_user': session_user,
        'admin_privilege': admin_privilege,
        'datatables_paginate': datatables_paginate,
        'page_title': page_title,
        'datatables_request_route_url': datatables_request_route_url
    }

@view_config(
    route_name = 'json.get_organization_shared_folders',
    renderer = 'json',
    permission = 'admin'
)
def json_get_organization_shared_folders(request):
    echo = request.params['sEcho']
    count = int(request.params['iDisplayLength'])
    offset = int(request.params['iDisplayStart'])

    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = helper_functions.get_rpc_stub(request)
    try:
        reply = sp.list_organization_shared_folders(count, offset)
        return _sp_reply2datatables(reply.shared_folder,
            reply.total_count, echo, get_session_user(request))
    except Exception as e:
        return {'error': parse_rpc_error_exception(request, e)}

def _json_get_user_shared_folders_url(request, user):
    return '{}?{}={}'.format(
        request.route_url('json.get_user_shared_folders'),
        URL_PARAM_USER,
        urllib.quote_plus(user)
    )

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
    sp = helper_functions.get_rpc_stub(request)
    try:
        reply = sp.list_user_shared_folders(specified_user)
        return _sp_reply2datatables(reply.shared_folder,
            len(reply.shared_folder), echo, get_session_user(request))
    except Exception as e:
        return {'error': parse_rpc_error_exception(request, e)}

def _sp_reply2datatables(shared_folders, total_count, echo, session_user):
    data = []
    for folder in shared_folders:
        data.append({
            'name': _render_shared_folder_options_link(folder, session_user),
            'users': _render_shared_folder_users(folder.user_and_role,
                session_user)
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

    str = ''
    index = 0
    total = len(reordered_list)
    for user_and_role in reordered_list:
        index += 1
        new_str = str

        # add connectors
        if index > 1:
            if total == 2: new_str += " and "
            elif index == total: new_str += ", and "
            else: new_str += ", "

        new_str += _ger_first_name(user_and_role.user, session_user)

        # truncate the string if it becomes too long
        left = total - index + 1
        if index > 1 and left > 1 and len(new_str) > 40:
            str += " and {} others".format(left)
            break
        else:
            str = new_str

    if total == 1: str += " only"

    return escape(str)

def _render_shared_folder_options_link(folder, session_user):
    id = encode_store_id(folder.store_id)
    folder_name = folder.name
    urs = to_json(folder.user_and_role, session_user)

    escaped_folder_name = escape(folder_name)

    # The data tags must be consistent with the ones in loadModalData() in
    # shared_folder.mako.
    # use single quote for data-srps since srps is in JSON which uses double
    # quotes.
    #
    # N.B. need to sub out quotes for proper rendering of the dialog.
    return u'<a href="#" class="{}" data-{}="{}" data-{}="{} "' \
           u'data-{}="{}">{}</a>'.format(
            _OPEN_MODAL_CLASS,
            _LINK_DATA_SID, escape(id),
            _LINK_DATA_NAME, escaped_folder_name,
            _LINK_DATA_USER_AND_ROLE_LIST, escape(urs).replace('"', '&#34;'),
            escaped_folder_name)

def to_json(user_and_role_list, session_user):

    # json.dumps() can't convert the object so we have to do it manually.
    urs = {}
    for ur in user_and_role_list:
        urs[ur.user.user_email] = {
            _USER_AND_ROLE_FIRST_NAME_KEY:
                _ger_first_name(ur.user, session_user),
            _USER_AND_ROLE_LAST_NAME_KEY:
                _get_last_name(ur.user, session_user),
            _USER_AND_ROLE_IS_OWNER_KEY:
                (1 if ur.role == common.OWNER else 0)
        }

    # dump to a compact-format JSON string
    return json.dumps(urs, separators=(',',':'))

def _ger_first_name(user, session_user):
    """
    @param user a PBUser object
    """
    return "me" if user.user_email == session_user else user.first_name

def _get_last_name(user, session_user):
    """
    otherwise.
    @param user a PBUser object
    """
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

    userid = request.params['userid']
    storeid = decode_store_id(request.params['storeid'])
    foldername = request.params['foldername']
    note = request.params.get('note') or ''

    role_pair = common.PBSubjectRolePair()
    role_pair.subject = userid
    # editor by default when added
    # TODO (WW) why not simply use common.EDITOR?
    role_pair.role = common._PBROLE.values_by_name['EDITOR'].number

    sp = get_rpc_stub(request)
    try:
        sp.share_folder(foldername, storeid, [role_pair], note)
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False,
                'response_message': error}

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

    userid = request.params['userid']
    storeid = decode_store_id(request.params['storeid'])
    permission = request.params['perm']

    role_pair = common.PBSubjectRolePair()
    role_pair.subject = userid
    role_pair.role = common._PBROLE.values_by_name[permission].number

    sp = get_rpc_stub(request)
    try:
        sp.update_acl(storeid, [role_pair])
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

    userid = request.params['userid']
    storeid = decode_store_id(request.params['storeid'])

    sp = get_rpc_stub(request)
    try:
        sp.delete_acl(storeid, [userid])
        return {'success': True}
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'success': False, 'response_message': error}

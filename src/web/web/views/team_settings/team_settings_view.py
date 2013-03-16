import logging, base64
from cgi import escape
from pyramid.view import view_config
from aerofs_sp.gen.sp_pb2 import USER, ADMIN
import aerofs_sp.gen.common_pb2 as common
from web.util import *

# URL param keys
from web.views.team_members.team_members_view import render_full_name

URL_PARAM_USER = 'user'
URL_PARAM_FULL_NAME = 'full_name'

log = logging.getLogger("web")

def get_permission(pbrole):
    return common._PBROLE.values_by_number[int(pbrole)].name

def encode_store_id(sid):
    return base64.b32encode(sid)

def decode_store_id(encoded_sid):
    return base64.b32decode(encoded_sid)

@view_config(
    route_name = 'team_settings',
    renderer = 'team_settings.mako',
    permission = 'admin'
)
def team_settings(request):
    _ = request.translate

    ret = {}
    sp = get_rpc_stub(request)

    # Only POST requests can modify state. See README.security.txt
    # TOOD (WW) separate POST and GET handling to different view callables
    if request.method == 'POST' and 'form.submitted' in request.params:
        form = {
            'organization_name': request.params['organization_name'].strip()
        }
        form.update(ret)

        try:
            sp.set_org_preferences(form['organization_name'], None)
            flash_success(request, _("Preferences have been updated."))
        except Exception as e:
            flash_error(request, parse_rpc_error_exception(request, e))

    reply = sp.get_org_preferences()
    ret['organization_name'] = reply.organization_name

    # Show billing links only if the user has a Stripe customer ID
    stripe_data = sp.get_stripe_data().stripe_data
    ret['show_billing'] = stripe_data.customer_id

    return ret

@view_config(
    route_name = 'json.user_lookup',
    renderer = 'json',
    permission = 'admin'
)
def json_user_lookup(request):
    search_term = str(request.params['searchTerm'])
    auth_level = str(request.params['authLevel'])
    if auth_level == "ADMIN":
        auth_level = ADMIN
    else:
        auth_level = USER

    count = int(request.params['count'])  # total number of results returned
    offset = int(request.params['offset'])  # start position
    sp = get_rpc_stub(request)
    try:
        reply = sp.list_users_auth(search_term, auth_level, count, offset)
        users = []
        for user in reply.users:
            users.append(str(user.user_email))
        return {
            'users': users
        }
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {
            'error': error
        }

@view_config(
    route_name = 'json.get_admins',
    renderer = 'json',
    permission = 'admin'
)
def json_get_admins(request):
    _ = request.translate

    # Since we don't use pagination for the administrator list, limit the number
    # of returned entries to prevent the server from exhausting memory when
    # composing the list. We should consider paging once some users hit the
    # limit.
    MAX_ADMIN_COUNT = 1000

    sp = get_rpc_stub(request)
    try:
        # list admins with empty search term
        reply = sp.list_users_auth("", ADMIN, MAX_ADMIN_COUNT, 0)
    except Exception as e:
        # returning this structure rather than an HTTP exception is required by
        # datatables_extentions.js to render error messages properly.
        return {'error' : parse_rpc_error_exception(request, e)}

    if reply.total_count > MAX_ADMIN_COUNT:
        log.error("Too many administrators to display: {}".format(
                reply.total_count))
        # see the comment for the above error return
        return {'error' : _("Too many administrators to display. " +
            "Please contact AeroFS support for assistance.")}

    user_id = request.session['username']

    return {
        "sEcho": str(request.GET['sEcho']),
        "iTotalRecords": reply.total_count,
        "iTotalDisplayRecords": reply.total_count,
        "aaData": [{
            "name": render_full_name(user),
            "email": escape(user.user_email),
            # Remember to update the $("#admins_table").on("click") callback in
            # team_settings.mako when changing the HTML class for the Remove button.
            # N.B. beware of XSS attacks.
            "action": '<a data-email="{}" class="remove-admin-btn"'
                    ' href="#">Remove</a>'.format(escape(user.user_email))
                    if user_id != user.user_email else ''
        } for user in reply.users]
    }

@view_config(
    route_name = 'json.set_authorization',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_set_authorization(request):
    _ = request.translate

    user = request.params['userid']
    if not is_valid_email(user):
        return {'response_message': _("Error: Invalid user id"),
                'success': False}
    level = request.params['authlevel']
    if level == 'USER':
        authlevel = USER
        msg = _("${user} is no longer an admin.", {'user': user})
    elif level == 'ADMIN':
        authlevel = ADMIN
        msg = _("${user} has been added as an admin.", {'user': user})
    else:
        return {'response_message': _("Error: Invalid authorization level"),
                'success': False}

    sp = get_rpc_stub(request)
    try:
        sp.set_authorization_level(user, authlevel)
        return {'response_message': msg,
                'success': True}
    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg,
                'success': False}

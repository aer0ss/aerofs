"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""
import logging, base64, urllib
from cgi import escape
from pyramid.view import view_config
from aerofs_sp.gen.sp_pb2 import USER, ADMIN
import aerofs_sp.gen.common_pb2 as common
from web import helper_functions
from web.helper_functions import *
from aerofs_common.exception import ExceptionReply
from ..payment.payment_view import update_stripe_subscription

# URL param keys
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
    route_name = 'admin_settings',
    renderer = 'settings.mako',
    permission = 'admin'
)
def admin_settings(request):
    _ = request.translate

    retVal = {}
    sp = get_rpc_stub(request)

    # Only POST requests can modify state. See README.security.txt
    # TOOD (WW) separate POST and GET handling to different view callables
    if request.method == 'POST' and 'form.submitted' in request.params:
        form = {
            'organization_name': request.params['organization_name'].strip()
        }
        form.update(retVal)

        try:
            sp.set_org_preferences(form['organization_name'], None)
            flash_success(request, _("Preferences have been updated."))
        except Exception as e:
            flash_error(request, parse_rpc_error_exception(request, e))

    try:
        reply = sp.get_org_preferences()
        retVal['organization_name'] = reply.organization_name

    except Exception as e:
        flash_error(request, parse_rpc_error_exception(request, e))
        return {
            # prevent rendering exceptions on RPC errors.
            'organization_name': ''
        }

    return retVal

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
            "name": _render_full_name(user),
            "email": escape(user.user_email),
            # Remember to update the $("#admins_table").on("click") callback in
            # settings.mako when changing the HTML class for the Remove button.
            # N.B. beware of XSS attacks.
            "action": '<a data-email="{}" class="remove-admin-btn"'
                    ' href="#">Remove</a>'.format(escape(user.user_email))
                    if user_id != user.user_email else ''
        } for user in reply.users]
    }


# Users view
# TODO (WW) move users view to a separate module or python file

@view_config(
    route_name = 'admin_users',
    renderer = 'users.mako',
    permission = 'admin'
)
def users_view(request):
    # It's very weird that if we use get_rpc_stub instead of
    # helper_functions.get_rpc_stub here, the unit test would fail.
    sp = helper_functions.get_rpc_stub(request)
    reply = sp.list_organization_invited_users()
    return {
        'url_param_user': URL_PARAM_USER,
        'invited_users': reply.user_id
    }

@view_config(
    route_name = 'json.get_users',
    renderer = 'json',
    permission = 'admin'
)
def json_get_users(request):
    echo = str(request.GET['sEcho'])
    count = int(request.GET['iDisplayLength'])
    offset = int(request.GET['iDisplayStart'])
    if 'sSearch' in request.GET:
        search = str(request.GET['sSearch'])
    else:
        search = ''

    sp = get_rpc_stub(request)
    try:
        reply = sp.list_users(search, count, offset)
    except Exception as e:
        error = parse_rpc_error_exception(request, e)
        return {'error': error}

    return {
        'sEcho': echo,
        'iTotalRecords': reply.total_count,
        'iTotalDisplayRecords': reply.filtered_count,
        'aaData':[{
            'name': _render_full_name(user),
            'email': escape(user.user_email),
            'options': _render_user_options_link(request, user)
        } for user in reply.users]
    }

def _render_user_options_link(request, user):
    coming_soon = ' class="coming_soon_link" rel="tooltip" ' \
                  'title="Coming soon" style="color: grey;"'

    email = urllib.quote_plus(user.user_email)
    full_name = urllib.quote_plus(user.first_name + ' ' + user.last_name)

    shared_folders_url = '{}?{}={}&{}={}'.format(
        request.route_url('user_shared_folders'),
        URL_PARAM_USER, email,
        URL_PARAM_FULL_NAME, full_name
    )

    devices_url = '{}?{}={}&{}={}'.format(
        request.route_url('user_devices'),
        URL_PARAM_USER, email,
        URL_PARAM_FULL_NAME, full_name
    )

    ## TODO (WW) writing raw HTML is so ugly. They should be generated by JS.
    return '<div class="dropdown">' \
            '<a class="dropdown-toggle" ' \
                    'data-toggle="dropdown" href="#">Options</a>' \
            '<ul class="dropdown-menu" role="menu">' \
                '<li><a href="' + shared_folders_url + '">Shared Folders</a>' \
                '<li><a href="' + devices_url + '">Devices</a>'\
                '<li class="divider"></li>' \
                '<li><a' + coming_soon + 'href="#">Remove</a>'\
            '</ul>' \
        '</div>'

def _render_full_name(user):
    return u"<span class='full_name'>{} {}</span>".format(
           escape(user.first_name), escape(user.last_name))

@view_config(
    route_name = 'json.invite_user',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_invite_user(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]

    if not userid_sanity_check(user):
        return {'response_message': _("Error: Invalid email address"),
                'success': False}
    sp = get_rpc_stub(request)
    try:
        # invite the user to sign up first
        # TODO (WW) this logic should be moved to sp.invite_to_organization()
        try:
            sp.invite_to_sign_up([user])
        except ExceptionReply as e:
            if e.get_type() == common.PBException.ALREADY_EXIST:
                ## Ignore if the user already exists
                pass
            else:
                raise e

        # invite the user to the org
        success = True
        try:
            stripe_subscription_data = sp.invite_to_organization(user)\
                .stripe_subscription_data

            # Since the organization now has one more user, adjust the
            # subscription for the org.
            #
            # TODO (WW) RACE CONDITION here if other users update the user list at the
            # same time!
            # TODO (WW) have an automatic tool to periodically check consistency
            # between SP and Stripe?
            update_stripe_subscription(stripe_subscription_data)

            # TODO (WW) change the way success and error messages are returned
            return {
                'response_message': _("${user} has been invited to your team.", {'user': user}),
                'success': True
            }

        except ExceptionReply as e:
            if e.get_type() == common.PBException.ALREADY_EXIST:
                return {
                    'response_message': _("${user} is already a member of your team.", {'user': user}),
                    'success': False
                }
            elif e.get_type() == common.PBException.ALREADY_INVITED:
                return {
                    'response_message': _("${user} has already been invited to your team.", {'user': user}),
                    'success': False
                }
            else:
                raise e

    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

@view_config(
    route_name = 'json.delete_organization_invitation_for_user',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_delete_organization_invitation_for_user(request):
    _ = request.translate

    user = request.params[URL_PARAM_USER]

    sp = get_rpc_stub(request)
    try:
        stripe_subscription_data = \
            sp.delete_organization_invitation_for_user(user)\
                .stripe_subscription_data

        # TODO (WW) RACE CONDITION here if other users update the user list at
        # the same time!
        # TODO (WW) have an automatic tool to periodically check consistency
        # between SP and Stripe?
        update_stripe_subscription(stripe_subscription_data)

        return {
            'response_message': _("The invitation has been removed."),
            'success': True
        }

    except Exception as e:
        msg = parse_rpc_error_exception(request, e)
        return {'response_message': msg, 'success': False}

@view_config(
    route_name = 'json.set_authorization',
    renderer = 'json',
    permission = 'admin',
    request_method = 'POST'
)
def json_set_authorization(request):
    _ = request.translate

    user = request.params['userid']
    if not userid_sanity_check(user):
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

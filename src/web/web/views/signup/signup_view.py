"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""
import logging
from pyramid.httpexceptions import HTTPBadRequest, HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_common.exception import ExceptionReply
import aerofs_sp.gen.common_pb2 as common
from web.sp_util import exception2error

from web.error import error
from web.util import get_error, get_rpc_stub, is_private_deployment, is_valid_password
from web.views.login.login_view import URL_PARAM_FORM_SUBMITTED, URL_PARAM_EMAIL, \
    URL_PARAM_PASSWORD, URL_PARAM_REMEMBER_ME
from web.login_util import URL_PARAM_NEXT

import analytics
import markupsafe

log = logging.getLogger(__name__)

# N.B. the string 'c' is also used in RequestToSignUpEmailer.java
# TODO (WW) use protobuf to share constants between Python and Java code?
URL_PARAM_SIGNUP_CODE = 'c'
URL_PARAM_FIRST_NAME = 'first_name'
URL_PARAM_LAST_NAME = 'last_name'
URL_PARAM_TITLE = 'title'
URL_PARAM_COMPANY = 'company'
URL_PARAM_COMPANY_SIZE = 'company_size'
URL_PARAM_PHONE = 'phone'
URL_PARAM_COUNTRY = 'country'

@view_config(
    route_name='signup',
    renderer='signup.mako',
    permission=NO_PERMISSION_REQUIRED,
)
def signup(request):
    _ = request.translate

    code = request.params.get(URL_PARAM_SIGNUP_CODE)

    if not code: raise HTTPBadRequest()

    try:
        sp = get_rpc_stub(request)
        email = sp.resolve_sign_up_code(code).email_address
    except ExceptionReply as e:
        if e.get_type() == common.PBException.NOT_FOUND:
            return HTTPFound(location=request.route_path('signup_code_not_found'))
        else:
            raise e

    return {
        'url_param_signup_code': URL_PARAM_SIGNUP_CODE,
        'url_param_form_submitted': URL_PARAM_FORM_SUBMITTED,
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_first_name': URL_PARAM_FIRST_NAME,
        'url_param_last_name': URL_PARAM_LAST_NAME,
        'url_param_title': URL_PARAM_TITLE,
        'url_param_company': URL_PARAM_COMPANY,
        'url_param_company_size': URL_PARAM_COMPANY_SIZE,
        'url_param_phone': URL_PARAM_PHONE,
        'url_param_country': URL_PARAM_COUNTRY,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'url_param_next': URL_PARAM_NEXT,
        'is_private_deployment': is_private_deployment(request.registry.settings),
        'email_address': email,
        'code': code
    }


@view_config(
    route_name='signup_code_not_found',
    renderer='signup_code_not_found.mako',
    permission=NO_PERMISSION_REQUIRED,
)
def signup_code_not_found(request):
    return {
        'support_email': request.registry.settings['base.www.support_email_address']
    }

@view_config(
    # Eric says: If we are returning a JSON document it would be more appropriate to
    # call it 'signup.json'. I know this is inconsistent with other view names..
    # those other view names are wrong also.
    # I also think we should specify the 'request_method' attribute here so that
    # it's clear that this is only for processing POST requests.
    route_name = 'json.signup',
    renderer = 'json',
    permission=NO_PERMISSION_REQUIRED
)
def json_signup(request):
    _ = request.translate

    code = request.params[URL_PARAM_SIGNUP_CODE]
    email_address = request.params[URL_PARAM_EMAIL]
    first_name = request.params[URL_PARAM_FIRST_NAME]
    last_name = request.params[URL_PARAM_LAST_NAME]
    password = request.params[URL_PARAM_PASSWORD].encode("utf-8")

    (valid_password, invalid_message) = is_valid_password(request, password)
    if not valid_password:
        return { 'error': invalid_message }

    try:
        sp = get_rpc_stub(request)
        sp.sign_up_with_code(code, password, first_name, last_name)

        # disable async because uWSGI seems to kill the analytics thread as soon as the process returns
        # instead of allowing it to finish

        if not is_private_deployment(request.registry.settings):
            analytics.init(request.registry.settings['segmentio.secret_key'], flush_at=1, async=False)

            context = {
                'providers': {
                    'Salesforce': 'true'
                },
                'Salesforce': {
                    'object': 'Lead',
                    'lookup': { 'email': markupsafe.escape(request.params['email']) }
                }
            }
            analytics.identify(request.params[URL_PARAM_EMAIL],
                {
                    'email': markupsafe.escape(request.params[URL_PARAM_EMAIL]),
                    'firstName': markupsafe.escape(request.params[URL_PARAM_FIRST_NAME]),
                    'lastName': markupsafe.escape(request.params[URL_PARAM_LAST_NAME]),
                    'company': markupsafe.escape(request.params[URL_PARAM_COMPANY]),
                    'title': markupsafe.escape(request.params[URL_PARAM_TITLE]),
                    'employees': markupsafe.escape(request.params[URL_PARAM_COMPANY_SIZE]),
                    'phone': markupsafe.escape(request.params[URL_PARAM_PHONE]),
                },
                context=context
            )
            analytics.track(markupsafe.escape(request.params[URL_PARAM_EMAIL]), "Signed Up For Hybrid Cloud");

        return {
            'email_address': email_address,
        }
    except ExceptionReply as e:
        if e.get_type() == common.PBException.BAD_CREDENTIAL:
            msg = "The account already exists.<br>" \
                  "Please try again with the password you used to create the account.<br>" \
                  "If you forget the password," \
                  " <a target='_blank' href='" + request.route_path('request_password_reset') + \
                  " '>click here</a> to reset it."
        elif e.get_type() == common.PBException.LICENSE_LIMIT:
            support_email = request.registry.settings.get('base.www.support_email_address')
            # TODO (WW): format the support email address as a mailto: link
            msg = "Adding your user account would cause your organization to exceed its licensed user limit.<br>" \
                  "Please contact your administrator at {}.".format(support_email)
        else:
            msg = get_error(e)
        return {'error': msg}


@view_config(
    route_name='json.request_to_signup',
    renderer='json',
    permission=NO_PERMISSION_REQUIRED,
    # See the comments in sign_up_forms.mako for the reason why we use GET but
    # not POST.
    request_method='GET'
)
def json_request_to_sign_up(request):
    _ = request.translate
    email_address = request.params[URL_PARAM_EMAIL]
    empty_email_message = _("Please enter an email address")
    if not email_address: error(empty_email_message)

    if not is_private_deployment(request.registry.settings):
        analytics.init(request.registry.settings['segmentio.secret_key'], flush_at=1, async=False)
        analytics.identify(request.params[URL_PARAM_EMAIL],{
                    'email': request.params[URL_PARAM_EMAIL]
                })

        analytics.track(request.params[URL_PARAM_EMAIL], "Signup Initiated for Hybrid Cloud");

    sp = get_rpc_stub(request)
    exception2error(sp.request_to_sign_up, email_address, {
        common.PBException.EMPTY_EMAIL_ADDRESS: empty_email_message,
        common.PBException.INVALID_EMAIL_ADDRESS: _("Please enter a valid email address"),
        common.PBException.NO_PERM: _("The first user has been created. New users "
                                      "can be created only by invitations from existing users.")
    })


@view_config(
    route_name='create_first_user',
    renderer='create_first_user.mako',
    permission=NO_PERMISSION_REQUIRED,
    request_method='GET'
)
def create_first_user(request):
    return {
        'url_param_email': URL_PARAM_EMAIL
    }

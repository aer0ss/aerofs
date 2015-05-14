"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""
import logging
import json
from pyramid.httpexceptions import HTTPBadRequest, HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from pyramid.response import Response

from aerofs_common.exception import ExceptionReply
import aerofs_sp.gen.common_pb2 as common
from web.sp_util import exception2error

from web.error import error
from web.util import get_error, get_rpc_stub, is_private_deployment, is_valid_password, send_sales_email
from web.views.login.login_view import URL_PARAM_EMAIL, URL_PARAM_PASSWORD, \
        URL_PARAM_REMEMBER_ME
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

@view_config(
    route_name='signup',
    renderer='signup.mako',
    permission=NO_PERMISSION_REQUIRED,
)
def signup(request):
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
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_first_name': URL_PARAM_FIRST_NAME,
        'url_param_last_name': URL_PARAM_LAST_NAME,
        'url_param_title': URL_PARAM_TITLE,
        'url_param_company': URL_PARAM_COMPANY,
        'url_param_company_size': URL_PARAM_COMPANY_SIZE,
        'url_param_phone': URL_PARAM_PHONE,
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
    code = markupsafe.escape(request.params[URL_PARAM_SIGNUP_CODE])
    email_address = markupsafe.escape(request.params[URL_PARAM_EMAIL])
    first_name = markupsafe.escape(request.params[URL_PARAM_FIRST_NAME])
    last_name = markupsafe.escape(request.params[URL_PARAM_LAST_NAME])
    company = markupsafe.escape(request.params[URL_PARAM_COMPANY])
    job_title = markupsafe.escape(request.params[URL_PARAM_TITLE]),
    employees = markupsafe.escape(request.params[URL_PARAM_COMPANY_SIZE])
    phone = markupsafe.escape(request.params[URL_PARAM_PHONE])
    password = request.params[URL_PARAM_PASSWORD].encode("utf-8")

    (valid_password, invalid_message) = is_valid_password(request, password)
    if not valid_password:
        return { 'error': invalid_message }

    try:
        sp = get_rpc_stub(request)
        sp.sign_up_with_code(code, password, first_name, last_name)

        return {
            'email_address': email_address,
            'firstName': first_name,
            'lastName': last_name,
            'company': company,
            'title': job_title,
            'employees': employees,
            'phone': phone
        }
    except ExceptionReply as e:
        if e.get_type() == common.PBException.BAD_CREDENTIAL:
            msg = "The account already exists. Please try again with the password you used to create the account. " \
                  "If you forgot your password, please try resetting it."
        elif e.get_type() == common.PBException.LICENSE_LIMIT:
            support_email = request.registry.settings.get('base.www.support_email_address')
            # TODO (WW) format the support email address as a mailto: link
            # TODO (MP) dedupe code in login_view.py
            msg = "Adding your user account would cause your organization to exceed its " + \
                  "licensed user limit. Please contact your administrator at " + \
                  "{}.".format(support_email)
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
    route_name='json.business_inquiry',
    renderer='json',
    permission=NO_PERMISSION_REQUIRED
    )
def json_business_inquiry(request):
    body = u''
    for key in request.params:
        body += "{}: {}\n".format(key, request.params[key])
    send_sales_email('webform@aerofs.com',
        "[BUSINESS_USER] Contact inquiry from {}".format(
        request.params['email']), body)

    r = Response(status=200)
    r.body = json.dumps({
        'email': request.params['email'],
        'first_name': request.params['first_name'],
        'last_name': request.params['last_name'],
        'company': request.params['org_name'],
        'title': request.params['title'],
        'employees': request.params['org_size'],
        'comment': request.params['comment'],
        'phone': request.params['phone'],
    })
    # use for testing from the static site only
    # r.headerlist = {'Access-Control-Allow-Origin': '*'}

    return r



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

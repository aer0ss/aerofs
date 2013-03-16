import logging

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPOk
from pyramid.security import NO_PERMISSION_REQUIRED

from web.util import get_rpc_stub

l = logging.getLogger("web")

@view_config(
    route_name = 'json.request_to_sign_up_with_business_plan',
    renderer = 'json',
    permission = NO_PERMISSION_REQUIRED,
    request_method = 'POST'
)
def request_to_sign_up_with_business_plan(request):
    email_address = request.params['email_address']

    sp = get_rpc_stub(request)
    sp.request_to_sign_up_with_business_plan(email_address)

    return HTTPOk()
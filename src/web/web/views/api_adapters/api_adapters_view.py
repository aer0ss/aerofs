import logging

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPOk, HTTPServerError
from pyramid.security import NO_PERMISSION_REQUIRED

from aerofs_common.exception import ExceptionReply
from web.util import get_error, get_rpc_stub

l = logging.getLogger("web")

@view_config(
    route_name = 'json.request_to_sign_up_with_business_plan',
    renderer = 'json',
    permission=NO_PERMISSION_REQUIRED
)
def request_to_sign_up_with_business_plan(request):
    email_address = request.params['email_address']

    try:
        sp = get_rpc_stub(request)
        sp.request_to_sign_up_with_business_plan(email_address)
    except ExceptionReply as e:
        l.error(e)
        raise HTTPServerError(detail=get_error(e))

    return HTTPOk()

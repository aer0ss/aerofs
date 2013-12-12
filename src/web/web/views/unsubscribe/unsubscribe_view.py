import logging
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.common_pb2 import PBException

from web.util import get_rpc_stub

log = logging.getLogger(__name__)

@view_config(
    route_name='unsubscribe',
    permission=NO_PERMISSION_REQUIRED,
    renderer='unsubscribe.mako'
)
def unsubscribe(request):
    """
    It's usually a GET request that unsubscribes the user, which violates the
    general guideline and bypasses CSRF verification (see README.security.txt).
    Fortunately, the subscription token acts as a guard against those attacks.
    """
    token = request.params.get("u")
    error = None
    not_found = False
    success = False
    email = None

    if token == None:
        error = "Invalid parameters"

    sp = get_rpc_stub(request)

    try:
        reply = sp.unsubscribe_email(token)
        email = reply.email_id
        success = True
    except PBException as pbe:
        if pbe.type == PBException.NOT_FOUND:
            not_found = True
            error = str(pbe)
            log.warn(error)
    except Exception as e:
        error = str(e)
        log.warn(error)

    return {
        "email":email,
        "not_found":not_found,
        "error":error,
        "success":success
    }

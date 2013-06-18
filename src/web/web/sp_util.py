import logging
from aerofs_common.exception import ExceptionReply
from web.error import error

log = logging.getLogger(__name__)

def exception2error(func, params, type2message_dict):
    """
    Call {@code func} with {@code params}. If an ExceptionReply is thrown from
    the func, raise an HTTP
    error response using the error() function. The type field in the response
    body is the ExceptionReply type name (e.g. NOT_FOUND). The
    message is determined by the type2message_dict parameter, which specifies a
    map from PBException types to error messages. If a type is not specified in
    the map, the PBException will be rethrown to the caller. Example:

        reply = exception2error(sp.some_rpc_method, (param1, param2), {
            PBException.ALREADY_EXIST: "the user already exists",
            PBException.ALREADY_INVITED: "the user is invited"
        })

    Note: all unhandled ExceptionReply exceptions are handled by
    error.py:protobuf_exception_view.
    """
    try:
        return _call(func, params)
    except ExceptionReply as e:
        if e.get_type() in type2message_dict:
            error(type2message_dict[e.get_type()], e.get_type_name())
        else:
            raise e

def _call(func, params):
    if isinstance(params, (list, tuple)):
        # use "splat" operator to expand the list into arguments
        return func(*params)
    elif params:
        return func(params)
    else:
        # use func() if params is an empty list; calling func(params) in this
        # case would become func(()). Is there a better way?
        return func()

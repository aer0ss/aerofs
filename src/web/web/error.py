"""
Error handling functions for JSON requests. See README.error.txt for detail.
"""
import json
import logging
from pyramid.httpexceptions import HTTPBadRequest, HTTPBadGateway

log = logging.getLogger(__name__)


def expected_error(message, type="unspecified", data=None):
    """
    Raise an HTTPBadRequest
    @param message A plaintext message that should include no markup.  The
                   frontend gets to decide how this will be displayed.
    """

    # return 400. See aerofs.js:showErrorMessageFromResponse() for the handling
    # code of this error.

    raise _create_error_response_object(HTTPBadRequest(), message, type, data)

def unexpected_error(message, type="unspecified", data=None):
    """
    Raise an HTTPBadGateway
    @param message A plaintext message that should include no markup.  The
                   frontend gets to decide how this will be displayed.
    """

    # return 502. This is currently used exclusively by bunker to prompt log download
    # when an unexpected error get caught.
    raise _create_error_response_object(HTTPBadGateway(), message, type, data)


def _create_error_response_object(error_type, message, type, data):
    response = error_type
    response.content_type = 'application/json'
    response.body = json.dumps(_form_json_body(message, type, data))

    return response

def _form_json_body(message, type, data):
    """
    Create JSON response object of form:
    {
        type: value of the type parameter
        message: value of the message parameter
        data: additional data for the Web frontend to process (optional)
    }

    The default "unspecified" type is not consumed by any code but is
    supposed be read by humans.
    """

    message = _normalize(message)

    log.error('error type: {}, message: "{}", data: "{}"'.format(type, message, data))

    json_map = {
        'type': type,
        'message': message
    }
    if data: json_map['data'] = data

    return json_map

def _normalize(message):
    """
    Capitalize the message and make sure it has a period at the end.
    Also see aerofs.js:normalize(). The JS method is needed despite of this
    Python method, since not all messages pass through this method.
    """
    if len(message) == 0:
        return message

    # capitalize the first letter
    message = message[:1].upper() + message[1:]

    # add an ending period if needed
    last_char = message[-1]
    if last_char not in ('.', '?', '!'): message += '.'

    return message

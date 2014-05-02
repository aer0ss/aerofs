"""
Error handling functions for JSON requests. See README.error.txt for detail.
"""
import json
import logging
from pyramid.httpexceptions import HTTPBadRequest

log = logging.getLogger(__name__)

def error(message, type="unspecified", data=None):
    """
    Raise an HTTPBadRequest object with a JSON body with the following format:
    {
        type: value of the type parameter
        message: value of the message parameter
        data: additional data for the Web frontend to process (optional)
    }

    Note that the default "unspecified" type is not consumed by any code but is
    supposed be read by humans.

    @param message A plaintext message that should include no markup.  The
                   frontend gets to decide how this will be displayed.
    """
    message = _normalize(message)

    log.error('error type: {}, message: "{}"'.format(type, message))

    json_map = {
        'type': type,
        'message': message
    }
    if data: json_map['data'] = data

    # return 400. See aerofs.js:showErrorMessageFromResponse() for the handling
    # code of this error.
    response = HTTPBadRequest()
    response.content_type = 'application/json'
    response.body = json.dumps(json_map)
    raise response

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

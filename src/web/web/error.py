"""
Error handling functions for JSON requests. See README.error.txt for detail.
"""
import json
import logging
from pyramid.httpexceptions import HTTPBadRequest

log = logging.getLogger(__name__)

def error(message, type="unspecified"):
    """
    Raise an HTTPBadRequest object with a JSON body with the following format:
    {
        type: value of the type parameter
        message: value of the message parameter
    }
    """
    message = _normalize(message)
    log.error('error message: "{}"'.format(message))

    response = HTTPBadRequest()
    response.content_type = 'application/json'
    response.body = json.dumps({
        'type' : type,
        'message': message
    })
    raise response

def _normalize(message):
    """
    Capitalize the message and make sure it has a period at the end.
    Also see aerofs.js:normalize(). The JS method is needed despite of this
    Python method, since not all messages pass through this method.
    """
    lastChar = message[-1]
    if lastChar not in ('.', '?', '!'): message += '.'
    return message.capitalize()
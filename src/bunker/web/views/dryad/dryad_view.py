import logging
import requests
import uuid

from pyramid.view import view_config

log = logging.getLogger(__name__)

_URL_PARAM_EMAIL = 'email'
_URL_PARAM_DESC = 'desc'
_URL_PARAM_USERS = 'user'

@view_config(
    route_name='report-problem',
    permission='maintain',
    renderer='dryad.mako',
    request_method='GET',
)
def report_problems(request):
    return {}


@view_config(
    route_name='json-submit-report',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def json_submit_problem(request):
    logging.info(request.body)

    # UUID Type 4 (random numbers)
    dryadID = uuid.uuid4()

    # echo for the time being to verify encoding and parsing
    return {
        'dryadID':  dryadID.hex,
        'email':    request.params[_URL_PARAM_EMAIL],
        'desc':     request.params[_URL_PARAM_DESC],
        'users':    request.params.getall(_URL_PARAM_USERS),
    }


@view_config(
    route_name='json-get-users',
    permission='maintain',
    renderer='json',
    request_method='GET',
)
def json_get_users(request):
    url = request.registry.settings['deployment.dryad_servlet_uri']
    r = requests.get(url)
    users = r.json()

    return {
        'users':    users,
    }

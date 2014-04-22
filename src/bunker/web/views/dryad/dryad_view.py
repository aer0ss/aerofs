import logging
import requests

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

    url = get_dryad_servlet_uri(request)
    payload = {
        'email':    request.params[_URL_PARAM_EMAIL],
        'desc':     request.params[_URL_PARAM_DESC],
        'users':    request.params.getall(_URL_PARAM_USERS),
    }

    r = requests.post(url, data=payload)

    # echo for the time being to verify encoding and parsing
    return {
        'status':   r.status_code,
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
    url = get_dryad_servlet_uri(request)
    r = requests.get(url)
    users = r.json()

    return {
        'users':    users,
    }


def get_dryad_servlet_uri(request):
    return request.registry.settings['deployment.dryad_servlet_uri']

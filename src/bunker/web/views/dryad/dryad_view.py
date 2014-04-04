import logging
from pyramid.view import view_config

log = logging.getLogger(__name__)

_URL_PARAM_EMAIL = 'email'
_URL_PARAM_DESC = 'desc'

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

    ## echo for the time being to verify encoding and parsing
    return {
        'email':    request.params[_URL_PARAM_EMAIL],
        'desc':     request.params[_URL_PARAM_DESC],
    }

import logging

import oursql
from pyramid.view import view_config

from web.views.maintenance.maintenance_util import get_conf

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

    # echo for the time being to verify encoding and parsing
    return {
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
    with get_spdb_conn(request) as cursor:
        cursor.execute('SELECT u_id FROM sp_user')

        # TODO (AT): this doesn't scale when result set is large
        users = map(lambda r: r[0], cursor.fetchall())

    return {
        'users': users
    }


def get_spdb_conn(request):
    return oursql.connect(host=get_conf(request)['base.host.unified'],
                          user='aerofs_sp',
                          db='aerofs_sp')

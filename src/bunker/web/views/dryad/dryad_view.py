from pyramid.view import view_config

@view_config(
    route_name='report-problems',
    permission='maintain',
    renderer='dryad.mako',
    request_method='GET',
)
def report_problems(request):
    return {}

import logging
from pyramid.view import view_config

log = logging.getLogger(__name__)

@view_config(
    route_name='csrf.json',
    permission='user',
    renderer='json',
    request_method='GET'
)
def csrf_json_view(request):
    """ Respond with a valid json doc with the 'csrf_token' key """
    try:
	csrf_token = request.session.get_csrf_token()
	return {'csrf_token': csrf_token}
    except Exception as e:
        log.exception(e)
        request.response.status = 500
        return {'error': '500', 'message': 'internal server error'}

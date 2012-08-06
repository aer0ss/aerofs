from pyramid.view import view_config

@view_config(route_name='home', renderer='templates/mytemplate.pt')
def home_view(request):
    return {'project':'bootstrap'}

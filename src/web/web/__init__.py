from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.url import route_url
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from views.login.login_view import groupfinder
from root_factory import RootFactory
from web.util import is_private_deployment, is_configuration_initialized
from pyramid.httpexceptions import HTTPFound
from pyramid.request import Request
from views.site_config.site_config_view import site_config_redirect
import views

class RedirectMiddleware(object):
    def __init__(self, application, settings):
        self.app = application
        self.settings = settings

    def noop(self, *args):
        self.app.complete = True

    def needs_redirect(self, environ):
        redirect_not_required = [
                '/site_config',
                '/site_config_redirect',
                '/json_config_hostname',
                '/json_config_email',
                '/json_config_certificate',
                '/json_config_apply',
                '/json_config_poll',
                '/json_config_finalize']

        # Need redirect if:
        #  1. Private deployment, and
        #  2. Configuration has not been initialized (first run), and
        #  3. The path requires a redirect.
        return is_private_deployment(self.settings) and \
               is_configuration_initialized(self.settings) == False and \
               environ['PATH_INFO'] not in redirect_not_required

    def __call__(self, environ, start_response):
        if self.needs_redirect(environ):
            request = self.app.request_factory(environ)
            response = self.app.invoke_subrequest(Request.blank('/site_config_redirect'))
            return response(environ, start_response)

        return self.app(environ, start_response)

def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    if settings['deployment.mode'] == 'private':
        configuration = Configuration()
        configuration.fetch_and_populate(settings)

    # Import template directories from views
    # TODO (WW) don't do this. Use renderer="<module>:templates/foo.mako" instead
    for view in views.__all__:
        settings['mako.directories'] += '\n{}.{}:templates'.format(views.__name__, view)

    authentication_policy = SessionAuthenticationPolicy(callback=groupfinder)
    authorization_policy = ACLAuthorizationPolicy()

    if is_private_deployment(settings) and not is_configuration_initialized(settings):
        authentication_policy=None
        authorization_policy=None

    config = Configurator(
        settings=settings,
        authentication_policy=authentication_policy,
        authorization_policy=authorization_policy,
        root_factory=RootFactory,
        session_factory=session_factory_from_settings(settings),
        autocommit=True,
        default_permission='admin'
    )

    # Localization settings
    config.add_translation_dirs('locale')

    # Static views
    config.add_static_view(settings['static.prefix'], 'static', cache_max_age=3600)

    # Special handling for installer prefix view.
    installer_prefix = settings['installer.prefix']
    if installer_prefix == 'static':
        config.add_static_view('static/installers', 'installer')
    else:
        config.add_static_view(installer_prefix, 'installer')

    # Use different home page for private and public deployment
    if is_private_deployment(settings):
        # The "/" URL string must be consistent with RequestToSignUpEmailer.java
        config.add_route('dashboard_home', '/')
        config.add_route('marketing_home', 'marketing_home')
    else:
        # The "home" URL string must be consistent with RequestToSignUpEmailer.java
        config.add_route('dashboard_home', 'home')
        config.add_route('marketing_home', '/')

    # Import routes from views
    for view in views.__all__:
        config.include('{}.{}'.format(views.__name__, view))

    config.scan()

    # Config commiting. Pyramid does some great config conflict detection. This conflict detection
    # is limited to configuration changes between commits. Since some of the default configuration
    # is overridden in modules, we have to commit the default configuration before including the
    # modules. Otherwise we get a ConfigurationConflictError.
    # See http://pyramid.readthedocs.org/en/latest/narr/advconfig.html for more details.
    config.commit()

    return RedirectMiddleware(config.make_wsgi_app(), settings)

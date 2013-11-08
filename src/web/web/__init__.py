import logging
from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from root_factory import RootFactory
from auth import get_principals
from license import is_license_present_and_valid
from util import is_private_deployment, is_configuration_initialized
from pyramid.request import Request
import views

class RedirectMiddleware(object):
    """
    This class exists to redirect the application to the setup page when the
    configuration system has not been initialized.
    """
    log = logging.getLogger(__name__)

    def __init__(self, application, settings):
        self.app = application
        self.settings = settings

    def noop(self, *args):
        self.app.complete = True

    def _should_redirect(self, environ):
        # Redirect all the pages to setup if:
        #  1. Private deployment, and
        #  2. Configuration has not been initialized (first run), or the license
        #     has expired, and
        #  3. The page is not a setup page.
        # See docs/design/site_setup_auth.md for more info.
        #
        # Note that static assets are served directly by nginx. We don't need to
        # consider them here.
        return is_private_deployment(self.settings) and \
                not self._is_config_inited_and_license_valid() and \
                not self._is_setup_page(environ)

    def _is_config_inited_and_license_valid(self):
        return is_configuration_initialized(self.settings) and \
               is_license_present_and_valid(self.settings)

    def _is_setup_page(self, environ):
        # [1:] is to remove the leading slash
        return environ['PATH_INFO'][1:] in views.maintenance.routes

    def __call__(self, environ, start_response):
        if self._should_redirect(environ):
            self.log.info("redirect {} to setup page".format(environ['PATH_INFO']))
            response = self.app.invoke_subrequest(Request.blank('/setup'))
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

    authentication_policy = SessionAuthenticationPolicy(callback=get_principals)
    # See root_factory.py for ACL definitions
    authorization_policy = ACLAuthorizationPolicy()

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

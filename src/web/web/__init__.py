from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from root_factory import RootFactory
from auth import get_principals
from util import is_private_deployment, is_configuration_initialized
import views


def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    if settings['deployment.mode'] == 'private':
        configuration = Configuration(settings['deployment.config_server_uri'])
        settings.update(configuration.server_properties())

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

    # Config committing. Pyramid does some great config conflict detection. This conflict detection
    # is limited to configuration changes between commits. Since some of the default configuration
    # is overridden in modules, we have to commit the default configuration before including the
    # modules. Otherwise we get a ConfigurationConflictError.
    # See http://pyramid.readthedocs.org/en/latest/narr/advconfig.html for more details.
    config.commit()

    return config.make_wsgi_app()

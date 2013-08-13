from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from views.login.login_view import groupfinder
from root_factory import RootFactory
import views

def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    mode = settings['deployment.mode']
    if mode == "private":
        CONFIG_URL_FILE = "/etc/aerofs/configuration.url"
        CACERT_FILE = "/etc/ssl/certs/AeroFS_CA.pem"

        configuration = Configuration(CONFIG_URL_FILE, CACERT_FILE)
        configuration.fetch_and_populate(settings)

    authn_policy = SessionAuthenticationPolicy(callback=groupfinder)
    authz_policy = ACLAuthorizationPolicy()
    admin_session_factory = session_factory_from_settings(settings)

    # Import template directories from views
    # TODO (WW) don't do this. Use renderer="<module>:templates/foo.mako" instead
    for view in views.__all__:
        settings['mako.directories'] += \
            '\n{}.{}:templates'.format(views.__name__, view)

    config = Configurator(
        settings=settings,
        authentication_policy=authn_policy,
        authorization_policy=authz_policy,
        root_factory=RootFactory,
        session_factory=admin_session_factory,
        autocommit=True,
        default_permission='admin'
    )

    # Localization settings
    config.add_translation_dirs('locale')

    # Static views
    config.add_static_view(settings['static.prefix'], 'static', cache_max_age=3600)

    # Special handling for installer prefix view.
    installer_prefix = settings['installer.prefix']
    if installer_prefix == "static":
        config.add_static_view("static/installers", 'installer')
    else:
        config.add_static_view(installer_prefix, 'installer')

    # Use different home page for private and public deployment
    if mode == "private":
        config.add_route('dashboard_home', '/')
        config.add_route('marketing_home', 'marketing_home')
    else:
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

    return config.make_wsgi_app()

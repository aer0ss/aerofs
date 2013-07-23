from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from views.login.login_view import groupfinder
from root_factory import RootFactory
from cStringIO import StringIO
import jprops
import requests
import views

def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    mode = settings['deployment.mode']
    if mode == "private":
        # Fetch dynamic configuration.  This must happen before we construct
        # the Configurator below.
        CONFIG_URL_FILE = "/etc/aerofs/configuration.url"
        CACERT_FILE = "/etc/ssl/certs/AeroFS_CA.pem"
        # TODO (DF): add SSL verification.  Left out to ease CA migration, should be added
        # back in before shipping to customers.
        verify = False
        #verify = CACERT_FILE
        config_url = None
        with open(CONFIG_URL_FILE) as f:
            config_url = f.read().strip()
        print "Loading dynamic configuration from {}".format(config_url)
        res = requests.get(config_url, verify=verify)
        if res.ok:
            props = jprops.load_properties(StringIO(res.text))
            for key in props:
                # Place all config key/value pairs in the global settings
                # object, except for server.wildcard.key because we really
                # should try to avoid leaving that lying around in case it
                # shows up in a debug message or something.
                if key == "server.wildcard.key":
                    continue
                settings[key] = props[key]
        else:
            raise IOError("Couldn't reach configuration server: {}".format(res.status_code))

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

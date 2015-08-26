import base64
import logging
import os
from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from root_factory import RootFactory
from auth import get_principals
import views

log = logging.getLogger(__name__)


def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    configuration = Configuration(settings['deployment.config_server_uri'], service_name='maintenance-web')
    settings.update(configuration.server_properties())

    _initialize_session_keys(settings)

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

    # Add support for Mako template engine
    config.include('pyramid_mako')

    # Localization settings
    config.add_translation_dirs('locale')

    # Static views
    config.add_static_view('admin/' + settings['static.prefix'], 'static', cache_max_age=3600)

    # Import routes from views
    for view in views.__all__:
        config.include('{}.{}'.format(views.__name__, view), route_prefix='/admin')

    config.scan()

    # Config commiting. Pyramid does some great config conflict detection. This conflict detection
    # is limited to configuration changes between commits. Since some of the default configuration
    # is overridden in modules, we have to commit the default configuration before including the
    # modules. Otherwise we get a ConfigurationConflictError.
    # See http://pyramid.readthedocs.org/en/latest/narr/advconfig.html for more details.
    config.commit()

    return config.make_wsgi_app()


def _initialize_session_keys(settings):
    """
    Generate per-appliance session validate and encrypt keys if they don't exist.
    See http://beaker.readthedocs.org/en/latest/modules/session.html and
    http://beaker.readthedocs.org/en/latest/configuration.html#session-options.
    """
    settings['session.validate_key'] = _read_or_create_key_file('session_validate_key', settings)
    settings['session.encrypt_key'] = _read_or_create_key_file('session_encrypt_key', settings)


def _read_or_create_key_file(file_name, settings):
    # The package installer creates and sets proper permissions on the folder /data/bunker
    path = os.path.join(settings.get("deployment.state_folder", "/data/bunker"), file_name)
    if os.path.exists(path):
        log.info('reading ' + path)
    else:
        log.info('generating ' + path)
        with open("/dev/urandom") as rng:
            key = base64.b64encode(rng.read(64))
        with open(path, "w") as f:
            f.write(key)

    with open(path) as f:
        return f.read()

import logging
from aerofs_common.configuration import Configuration
from pyramid.config import Configurator
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid_beaker import session_factory_from_settings
import views


log = logging.getLogger(__name__)


def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    configuration = Configuration(settings['deployment.config_server_uri'], service_name='maintenance-web')
    settings.update(configuration.server_properties())

    # Import template directories from views
    for view in views.__all__:
        settings['mako.directories'] += '\n{}.{}:templates'.format(views.__name__, view)

    config = Configurator(
        settings=settings,
        autocommit=True,
        session_factory=session_factory_from_settings(settings),
        default_permission=NO_PERMISSION_REQUIRED
    )

    # Add support for Mako template engine
    config.include('pyramid_mako')

    # Static views
    config.add_static_view(settings['static.prefix'], 'static', cache_max_age=3600)

    # Import routes from views
    for view in views.__all__:
        config.include('{}.{}'.format(views.__name__, view))

    config.scan()
    config.commit()

    return config.make_wsgi_app()

from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings
from modules.login.views import groupfinder
from modules.login.models import RootFactory
import re
import modules

# N.B. this is required for config.scan() to work, do not remove.
from modules import *

def main(global_config, **settings):
    """
    This function returns a Pyramid WSGI application.
    """

    authn_policy = SessionAuthenticationPolicy(callback=groupfinder)
    authz_policy = ACLAuthorizationPolicy()
    admin_session_factory = session_factory_from_settings(settings)

    modulePackageName = 'modules.'

    # Regular expression for python builtin module names (i.e. __init__)
    builtinfunc = re.compile('__\w+__')

    # Import template directories from modules
    for module in dir(modules):
        if not builtinfunc.match(module):
            settings['mako.directories'] += '\n' + modulePackageName + module + ':templates'

    config = Configurator(
        settings=settings,
        authentication_policy=authn_policy,
        authorization_policy=authz_policy,
        root_factory=RootFactory,
        session_factory=admin_session_factory,
        autocommit=True,
        default_permission='admin'
    )

    # Event subscribers
    config.add_subscriber('web.subscribers.add_renderer_globals', 'pyramid.events.BeforeRender')
    config.add_subscriber('web.subscribers.add_localizer', 'pyramid.events.NewRequest')

    # Localization settings
    config.add_translation_dirs('../locale/')

    # Global routes
    static_prefix = settings['static.prefix']
    config.add_static_view(static_prefix, 'aerofs_web.layout:static/', cache_max_age=3600)
    config.add_route('homepage', '/')

    config.scan()
    # Config commiting. Pyramid does some great config conflict detection. This conflict detection
    # is limited to configuration changes between commits. Since some of the default configuration
    # is overridden in modules, we have to commit the default configuration before including the
    # modules. Otherwise we get a ConfigurationConflictError.
    # See http://pyramid.readthedocs.org/en/latest/narr/advconfig.html for more details.
    config.commit()

    # Import routes from modules
    for module in dir(modules):
        if not builtinfunc.match(module):
            print modulePackageName + module
            config.include(modulePackageName + module)
            config.scan(package=modulePackageName + module)

    return config.make_wsgi_app()

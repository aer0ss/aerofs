from pyramid.config import Configurator
from pyramid.authentication import SessionAuthenticationPolicy
from pyramid.authorization import ACLAuthorizationPolicy
from pyramid_beaker import session_factory_from_settings

from modules.login.views import groupfinder
from modules.login.models import RootFactory

import re
import modules
from modules import *

def main(global_config, **settings):
    """ This function returns a Pyramid WSGI application.
    """

    authn_policy = SessionAuthenticationPolicy(callback=groupfinder)
    authz_policy = ACLAuthorizationPolicy()
    admin_session_factory = session_factory_from_settings(settings)

    config = Configurator(
        settings=settings,
        authentication_policy=authn_policy,
        authorization_policy=authz_policy,
        root_factory=RootFactory,
        session_factory=admin_session_factory,
        default_permission='admin' # By default, all views require the admin permission
    )

    # Event subscribers
    config.add_subscriber('web.subscribers.add_renderer_globals', 'pyramid.events.BeforeRender')
    config.add_subscriber('web.subscribers.add_localizer', 'pyramid.events.NewRequest')

    # Localization settings
    config.add_translation_dirs('../locale/')

    # Global routes
    config.add_static_view('common', 'aerofs_web.layout:static/', cache_max_age=3600)
    config.add_route('homepage', '/')

    # Import routes from modules
    builtinfunc = re.compile('__\w+__') # Regular expression for python builtin module names (i.e. __init__)
    for module in dir(modules):
        if not builtinfunc.match(module):
            config.include('modules.' + module)
            config.scan(package='modules.' + module)

    config.scan()
    return config.make_wsgi_app()

import logging
from pyramid.events import subscriber, NewRequest, BeforeRender
from pyramid.httpexceptions import HTTPUnauthorized
from pyramid.i18n import TranslationStringFactory, get_localizer
from aerofs_web.helper_functions import is_logged_in, get_session_user

@subscriber(NewRequest)
def validate_csrf_token(event):
    """
    Validate CSRF tokens for all non-GET requests. See README.security.txt
    """
    request = event.request
    csrf = request.params.get('csrf_token')

    if request.method != 'GET' and is_logged_in(request) and\
            csrf != unicode(request.session.get_csrf_token()):
        logging.getLogger("web").warn("CSRF validation failed. user {} path {}"
                .format(get_session_user(request), request.path))
        raise HTTPUnauthorized

@subscriber(BeforeRender)
def add_renderer_globals(event):
    """
    This allows mako to use _ as a translation function when rendering templates
    """
    request = event['request']
    event['_'] = request.translate
    event['localizer'] = request.localizer

tFactory = TranslationStringFactory('web')

@subscriber(NewRequest)
def add_localizer(event):
    """
    Add a translation function for the current request's locale to the request
    object for later use. Will eventually use a locale setting in
    request.session once users have the ability to set their own locale.
    """
    request = event.request
    localizer = get_localizer(request)

    def auto_translate(*args, **kwargs):
        return localizer.translate(tFactory(*args, **kwargs))

    request.localizer = localizer
    request.translate = auto_translate

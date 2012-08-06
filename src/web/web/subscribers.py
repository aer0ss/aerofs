from pyramid.i18n import TranslationStringFactory, get_localizer

def add_renderer_globals(event):
    ''' This allows mako to use _ as a translation function
        when rendering templates
    '''
    request = event['request']
    event['_'] = request.translate
    event['localizer'] = request.localizer

tFactory = TranslationStringFactory('web')

def add_localizer(event):
    ''' Add a translation function for the current request's
        locale to the request object for later use.
        Will eventually use a locale setting in request.session
        once users have the ability to set their own locale.
    '''
    request = event.request
    localizer = get_localizer(request)

    def auto_translate(*args, **kwargs):
        return localizer.translate(tFactory(*args, **kwargs))

    request.localizer = localizer
    request.translate = auto_translate

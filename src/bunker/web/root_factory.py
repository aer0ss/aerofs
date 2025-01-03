from pyramid.security import Allow, DENY_ALL
from web.auth import GROUP_ID_MAINTAINERS


class RootFactory(object):
    __name__ = ''
    __parent__ = None

    __acl__ = [
        (Allow, GROUP_ID_MAINTAINERS, 'maintain'),
        DENY_ALL
    ]

    def __init__(self, request):
        self.request = request

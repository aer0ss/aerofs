from pyramid.security import Allow, DENY_ALL
from web.auth import GROUP_ID_ADMINS, GROUP_ID_USERS, \
        GROUP_ID_TWO_FACTOR_LOGIN, GROUP_ID_TWO_FACTOR_SETUP


class RootFactory(object):
    __name__ = ''
    __parent__ = None

    __acl__ = [
        (Allow, GROUP_ID_ADMINS, ['admin']),
        (Allow, GROUP_ID_USERS, ['user']),
        (Allow, GROUP_ID_TWO_FACTOR_LOGIN, ['two_factor_login']),
        (Allow, GROUP_ID_TWO_FACTOR_SETUP, ['two_factor_setup']),
        DENY_ALL
    ]

    def __init__(self, request):
        self.request = request

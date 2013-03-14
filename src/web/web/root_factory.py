from aerofs_sp.gen.sp_pb2 import ADMIN, USER
from pyramid.security import Allow, DENY_ALL

class RootFactory(object):
    __name__ = ''
    __parent__ = None

    __acl__ = [
        (Allow, ADMIN, ['admin', 'user']),
        (Allow, USER, 'user'),
        DENY_ALL
    ]

    def __init__(self, request):
        self.request = request

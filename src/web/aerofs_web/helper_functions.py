'''
Helper functions for AeroFS website
'''

import re
from aerofs.connection import ExceptionReply, SyncConnectionService
from aerofs.gen.sp_pb2 import SPServiceRpcStub

def userid_sanity_check(userid):
    return re.match(r"[^@'\";]+@[^@'\";]+\.[^@'\";]+", userid)

def domain_sanity_check(domain):
    return len(domain) > 3 and domain.find(' ') < 0 and domain.find('.') >= 0

def get_error(exception): # parse RPC errors into something more user friendly
    return exception.reply.localized_message

def parse_rpc_error_exception(request, e):
    _ = request.translate
    if type(e) == ExceptionReply: # RPC Error
        return _("Error: ${error}", {'error': get_error(e)})
    else: # Internal server error
        return _("Internal server error: ${error}", mapping={'error': str(e)})

def get_rpc_stub(request):
    settings = request.registry.settings
    if 'SPCookie' in request.session: # attempt session recovery
        con = SyncConnectionService(settings['sp.url'], settings['sp.version'], request.session['SPCookie'])
    else:
        con = SyncConnectionService(settings['sp.url'], settings['sp.version'])
    return SPServiceRpcStub(con)

def flash_error(request, error_msg):
    request.session.flash(error_msg, 'error_queue')

def flash_success(request, success_msg):
    request.session.flash(success_msg, 'success_queue')

import sys
import stat
import argparse
import os
import code
import ConfigParser

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root,"../src/python-lib")
sys.path.append(python_aerofs_lib)

from aerofs_sp import connection
from aerofs_sp.gen import sp_pb2

config = ConfigParser.ConfigParser()
config.readfp(open('../src/web/development/modes/private.ini'))

parser = argparse.ArgumentParser(description=
        'A Command Line REPL for the SP RPC service. ' +
        'By default, SP runner will point to local prod.')

parser.add_argument('sp_url',
        nargs='?',
        help="The complete URL of the SP Service.")
parser.add_argument('sp_version',
        type=int,
        nargs='?',
        help="The version number to use when connecting.")

def interact_readfunc(prompt):
    for line in sys.stdin:
        return line
    raise EOFError

if __name__ == "__main__":
    args = parser.parse_args()

    url = args.sp_url or config.get('app:main', 'deployment.sp_server_uri')
    version = args.sp_version or config.get('app:main', 'sp.version')

    conn = connection.SyncConnectionService(url, version)
    sp = sp_pb2.SPServiceRpcStub(conn)

    class Command:
        def __init__(self,fn):
            self.fn= fn
        def __repr__(self):
            return self.fn()

    help = "The following RPC calls are available (see sp.proto for details):\n\n"
    commands = {}
    for x in dir(sp):
        if x[:1] != "_":
            help +=  "    " + x + "\n"
            commands[x] = getattr(sp, x)

    help += "\nUsers should call credential_sign_in first when using privileged calls."
    commands["help"] = Command(lambda : help.strip())

    mode = os.fstat(0).st_mode
    if stat.S_ISFIFO(mode):
        code.interact(banner=">>> Running piped commands...", local=commands, readfunc=interact_readfunc)
    else:
        code.interact(banner="Type help", local=commands)


#!/usr/bin/python
import sys
import argparse
import os
import code

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root,"../java/aerofs.python-lib")
sys.path.append(python_aerofs_lib)

from aerofs.scrypt import scrypt
from aerofs import connection
from aerofs.gen import sp_pb2

class Credentials:
    user_id = None
    @classmethod
    def set_user_id(klass,u_id):
        klass.user_id = u_id
    @classmethod
    def hash_password(klass,password):
        if klass.user_id == None:
            raise Exception("set_user_id must be called first in order to user hash_password")
        return scrypt(password,klass.user_id)

parser = argparse.ArgumentParser(description='A Command Line REPL for the SP RPC service')
parser.add_argument('server', help="The complete url of the server to connect to")

if __name__ == "__main__":
    args = parser.parse_args()
    url = args.server
    conn = connection.SyncConnectionService(url,7)
    sp = sp_pb2.SPServiceRpcStub(conn)
    class Command:
        def __init__(self,fn):
            self.fn= fn
        def __repr__(self):
            return self.fn()
    help = "The following rpc calls are available:\n"
    commands = {}
    for x in dir(sp):
        if x[:2] != "__":
            help +=  "\t" + x + "\n"
            commands[x] = getattr(sp,x)
    help += "Helper methods:\n"
    help += "\tset_user_id\n"
    help += "\t\tthis accepts a user id (email) and uses it whenever you call hash_password\n"
    help += "\thash_password\n"
    help += "\t\tset_user_id must be called first.  hash_password"
    help += " accepts a password and returns the scrypt hash\n"
    commands["help"] = Command(lambda : help)
    commands["scrypt"]=scrypt
    commands["set_user_id"]=Credentials.set_user_id
    commands["hash_password"]=Credentials.hash_password
    code.interact(banner="Type help", local=commands)

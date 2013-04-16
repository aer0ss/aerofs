#!/usr/bin/python

import os
import sys
from subprocess import check_output

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root,"../src/python-lib")
sys.path.append(python_aerofs_lib)

from aerofs_sp.scrypt import scrypt
from aerofs_sp import connection
from aerofs_sp.gen import sp_pb2

url = "https://spci.aerofs.com/sp"

# this version will probably change and break things
# in fact, this whole script is fragile.
conn = connection.SyncConnectionService(url, 20)

sp = sp_pb2.SPServiceRpcStub(conn)

def create_user(username, password):
    invite_script = "{0} peter@aerofs.com {1}".format(os.path.join(file_root, "invite.ci"), username)
    print invite_script
    code = check_output(invite_script, shell=True)
    print code
    code = code.split("code:")[-1].strip()
    sp.sign_up_with_code(code, scrypt(password, username), "P", "H")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print "Usage: {0} <username> <password>".format(sys.argv[0])
        exit(1)
    create_user(sys.argv[1], sys.argv[2])
    exit(0)

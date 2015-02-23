from binascii import unhexlify

from syncdet import case
from syncdet.actors import actor_list

from aerofs_common import exception
from aerofs_common.convert import store_relative_to_pbpath
from aerofs_ritual.gen import common_pb2
from aerofs_ritual.gen.common_pb2 import PBException
from aerofs_ritual.id import get_root_sid_bytes
from aerofs_sp import sp as sp_service
from lib import ritual


def ts_user_path(relative):
    return store_relative_to_pbpath(get_root_sid_bytes(actor_list()[-1].aero_userid), relative)

def ts_sid(store_name):
    sp = sp_service.connect()
    sp.sign_in(actor_list()[1])
    for folder in sp.list_shared_folders_with_names():
        if folder["name"] == store_name:
            return folder["sid"]

def ts_shared_path(store_name, relative):
    sid = ts_sid(store_name)
    return store_relative_to_pbpath(unhexlify(sid), relative)

def ritual_write_file(path, content):
    ritual.connect().write_file(path, content)

def ritual_write_pbpath(pbpath, content):
    ritual.connect().write_pbpath(pbpath, content)

def ritual_wait_file_with_content(path, content):
    ritual.connect().wait_file_with_content(path, content)

def ritual_wait_pbpath_with_content(pbpath, content):
    ritual.connect().wait_pbpath_with_content(pbpath, content)

def ritual_mkdir(path):
    ritual.connect().create_object(path, True)

def ritual_mkdir_pbpath(pbpath):
    ritual.connect().create_pbpath(pbpath, True)

def ritual_mv(pathFrom, pathTo):
    ritual.connect().move_object(pathFrom, pathTo)

def ritual_rm(path):
    ritual.connect().delete_object(path)

def ritual_wait_path_to_disappear(path):
    ritual.connect().wait_path_to_disappear(path)

def ritual_wait_pbpath_to_disappear(pbpath):
    ritual.connect().wait_pbpath_to_disappear(pbpath)

def ritual_wait_shared(path):
    r = ritual.connect()
    r.wait_path(path)
    while True:
        if path in r.list_shared_folders():
            break

def ritual_list_versions(path):
    return sorted(ritual.connect().list_rev_history(path), key=lambda e: e.mtime)

def ritual_fetch_version(path, index):
    with open(ritual.connect().export_revision(path, index), 'rb') as f:
        return f.readline()

def ritual_fetch_all_versions(path):
    return [ritual_fetch_version(path, c.index) for c in ritual_list_versions(path)]

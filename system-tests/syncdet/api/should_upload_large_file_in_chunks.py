import json
import os
import time

import requests
from syncdet.case import local_actor, fail_test_case

from core.block_storage import common
from lib import ritual
from lib.app.cfg import is_teamserver
from lib.files import instance_unique_path


def main():
    API_URL = "https://{}/api/v1.0".format(local_actor().aero_host)
    LOGIN_URL = "https://{}/login_for_tests.json".format(local_actor().aero_host)
    TOKEN_URL = "https://{}/json_new_token".format(local_actor().aero_host)
    CSRF_URL = "https://{}/csrf.json".format(local_actor().aero_host)

    # create this test's unique folder and wait for the daemon to notice it
    dirname = os.path.basename(instance_unique_path())
    print 'creating', dirname
    if is_teamserver():
        common.ritual_mkdir_pbpath(common.ts_user_path(dirname))
    else:
        ritual.connect().create_object(instance_unique_path(), True)

    # get a token through the web for ease
    s = requests.Session()
    r = s.get(LOGIN_URL, params={'email': local_actor().aero_userid, 'password': local_actor().aero_password})
    r.raise_for_status()
    r = s.get('CSRF_URL')
    r.raise_for_status()
    csrf_token = r.json()['csrf_token']
    r = s.post(TOKEN_URL, headers={'X-CSRF-Token': csrf_token})
    r.raise_for_status()
    token = r.json()['token']
    print 'token is', token

    s = requests.Session()
    s.headers['Authorization'] = 'Bearer ' + token
    s.headers['Endpoint-Consistency'] = 'strict'

    # get the id of this test's unique folder, which will be a child of the root anchor
    folder_id = None
    while True:
        r = s.get(API_URL+"/children/")
        r.raise_for_status()
        match = [f for f in r.json()['folders'] if f['name'] == dirname]
        if len(match) == 1:
            folder_id = match[0]['id']
            break
        time.sleep(0.2)

    print 'folder_id is', folder_id

    # create a file
    print 'creating file...'
    filename = 'fatfile.big'
    r = s.post(API_URL+'/files',
            headers={'Content-Type': 'application/json'},
            data=json.dumps({'parent': folder_id, 'name': filename}))
    r.raise_for_status()
    etag = r.headers['etag']
    file_id = r.json()['id']
    print 'etag:', etag, 'id:', file_id

    # upload 1M
    print 'uploading 1M of hihihihi...'
    s.headers['Content-Type'] = 'application/octet-stream'
    r = s.put(API_URL+'/files/'+file_id+'/content',
            headers={'Content-Range': 'bytes 0-1048575/3145728', 'If-None-Match': etag},
            data=('hi' * 512 * 1024).encode('utf-8'))
    r.raise_for_status()
    upload_id = r.headers['upload-id']
    print 'upload id is', upload_id

    # upload another 1M
    print 'uploading 1M of yoyoyoyo...'
    s.headers['upload-id'] = upload_id
    r = s.put(API_URL+'/files/'+file_id+'/content',
            headers={'Content-Range': 'bytes 1048576-2097151/3145728'},
            data=('yo' * 512 * 1024).encode('utf-8'))
    r.raise_for_status()

    # upload the last 1M
    print 'uploading 1M of lalalala...'
    s.headers['upload-id'] = upload_id
    r = s.put(API_URL+'/files/'+file_id+'/content',
            headers={'Content-Range': 'bytes 2097152-3145727/3145728'},
            data=('la' * 512 * 1024).encode('utf-8'))
    r.raise_for_status()

    # download the file metadata and check the size
    print 'downloading file metadata...'
    del s.headers['upload-id']
    del s.headers['Content-Type']
    r = s.get(API_URL+'/files/'+file_id)
    r.raise_for_status()
    assert r.json()['size'] == 1024 * 1024 * 3

    # download the file contents and check that they are correct
    print 'downloading file contents...'
    r = s.get(API_URL+'/files/'+file_id+'/content')
    r.raise_for_status()
    c = requests.utils.dict_from_cookiejar(r.cookies)
    print 'received {}/3145728 bytes from {}'.format(len(r.text), c['route'])
    expected = 'hi' * 512 * 1024 + 'yo' * 512 * 1024 + 'la' * 512 * 1024
    i = first_diff(expected, r.text)
    if i:
        print 'actual diverges from expected starting at {}'.format(i)
        if i == len(r.text):
            print 'missing {} bytes'.format(len(expected) - len(r.text))
        else:
            print 'actual  ={}'.format(r.text[i:])
            print 'expected={}'.format(expected[i:])
        fail_test_case("corrupted download")


def first_diff(expected, actual):
    l = min(len(expected), len(actual))
    for i in xrange(l):
        if expected[i] != actual[i]:
            return i
    if len(expected) != len(actual):
        return l
    return None


spec = {"entries": [main]}


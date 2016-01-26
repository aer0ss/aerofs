#!/usr/bin/env python

import json
import os.path
import requests
import subprocess
import sys
import time
import os

BASE_URL = "http://localhost:8001"
JGRAY_AUTH = {"Authorization": "Bearer jgray"}
DGRAY_AUTH = {"Authorization": "Bearer dgray"}
GGRAY_AUTH = {"Authorization": "Bearer ggray"}
BAD_AUTH = {"Authorization": "Bearer badtoken"}

# 2x2 red pixels, in GIF format
RED_IMG_DATA = 'R0lGODdhAgACALMAAAAAAP///wAAAAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACwAAAAAAgACAAAEA5BIEgA7'.decode('base64')

# clear db
print 'clearing db...'
this_dir = os.path.dirname(sys.argv[0])
subprocess.check_call('/usr/bin/env mysql -uroot -e "DROP DATABASE IF EXISTS slothtest"', shell=True)
print 'db is clear, starting tests...'

# build and run the server in the background
subprocess.check_call('make clean sloth', shell=True)
sloth_server = subprocess.Popen('./sloth -db="slothtest" -verifier="echo" -pushEnabled=false', shell=True)

try:
    # FIXME: poll with timeout instead
    time.sleep(1)

    # set headers
    s = requests.session()
    s.headers["Accept"] = "application/json"
    s.headers["Content-Type"] = "application/json"

    # GET /users no auth
    r = s.get(BASE_URL + "/users")
    assert r.status_code == 401, r

    # GET /users
    r = s.get(BASE_URL + "/users", headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["users"] == [], r.text

    # PUT /users/jgray with no body
    r = s.put(BASE_URL + "/users/jgray", headers=JGRAY_AUTH)
    assert r.status_code == 400, r

    # PUT /users/jgray with missing last name
    r = s.put(BASE_URL + "/users/jgray",
            json.dumps({"firstName": "Jonathan"}),
            headers=JGRAY_AUTH)
    assert r.status_code == 400, r

    # PUT /users/jgray with no token
    r = s.put(BASE_URL + "/users/jgray",
            json.dumps({"firstName": "Jonathan", "lastName": "Gray"}))
    assert r.status_code == 401, r

    # PUT /users/jgray with bad token
    r = s.put(BASE_URL + "/users/jgray",
            json.dumps({"firstName": "Jonathan", "lastName": "Gray"}),
            headers=BAD_AUTH)
    assert r.status_code == 403, r

    # PUT /users/jgray (default tagId)
    r = s.put(BASE_URL + "/users/jgray",
            json.dumps({"firstName": "Jonathan", "lastName": "Gray"}),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["firstName"] == "Jonathan", r.text
    assert r.json()["lastName"] == "Gray", r.text
    assert r.json()["id"] == "jgray", r.text
    tagId = r.json()["tagId"]
    assert tagId != None

    # PUT /users/dgray conflict tagId
    r = s.put(BASE_URL + "/users/dgray",
            json.dumps({
                "firstName": "Daniel",
                "lastName": "Gray",
                "tagId": tagId
            }),
            headers=DGRAY_AUTH)
    assert r.status_code == 409, r

    # PUT /users/dgray conflict default tagId
    r = s.put(BASE_URL + "/users/dgray",
            json.dumps({"firstName": "Jonathan", "lastName": "Gray"}),
            headers=DGRAY_AUTH)
    assert r.ok, r
    assert r.json()["tagId"] != tagId, r.text

    # PUT /users/dgray
    r = s.put(BASE_URL + "/users/dgray",
            json.dumps({
                "firstName": "Daniel",
                "lastName": "Gray",
                "tagId": "dg"
            }),
            headers=DGRAY_AUTH)
    assert r.ok, r
    assert r.json()["firstName"] == "Daniel", r.text
    assert r.json()["lastName"] == "Gray", r.text
    assert r.json()["id"] == "dgray", r.text
    assert r.json()["tagId"] == "dg", r.text
    assert "avatarPath" not in r.json(), r.text

    # PUT /users/ggray (with phone)
    r = s.put(BASE_URL + "/users/ggray",
            json.dumps({
                "firstName": "Gary",
                "lastName": "Gray",
                "tagId": "ggray",
                "phone": "4161234567"
            }),
            headers=GGRAY_AUTH)
    assert r.ok, r

    # GET /users
    r = s.get(BASE_URL + "/users", headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["users"][0]["id"] == "dgray", r.text
    assert r.json()["users"][1]["id"] == "ggray", r.text
    assert r.json()["users"][2]["id"] == "jgray", r.text
    assert r.json()["users"][1]["phone"] == "4161234567", r.text

    # GET /users/jgray no auth
    r = s.get(BASE_URL + "/users/jgray")
    assert r.status_code == 401, r

    # GET /users/nobody
    r = s.get(BASE_URL + "/users/nobody", headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # GET /users/jgray
    r = s.get(BASE_URL + "/users/jgray", headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["firstName"] == "Jonathan", r.text
    assert r.json()["lastName"] == "Gray", r.text
    assert r.json()["id"] == "jgray", r.text

    # GET /groups no auth
    r = s.get(BASE_URL + "/groups")
    assert r.status_code == 401, r

    # GET /groups
    r = s.get(BASE_URL + "/groups", headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get("groups") == [], r.text

    # POST /groups missing key
    r = s.post(BASE_URL + "/groups",
            json.dumps({
                "name": "F!RST GROUP",
                "members": ["dgray", "jgray"],
            }),
            headers=JGRAY_AUTH)
    assert r.status_code == 400, r

    # POST /groups no auth
    r = s.post(BASE_URL + "/groups",
            json.dumps({
                "name": "F!RST GROUP",
                "isPublic": True,
                "members": ["dgray", "jgray"],
            }))
    assert r.status_code == 401, r

    # POST /groups
    r = s.post(BASE_URL + "/groups",
            json.dumps({
                "name": "F!RST GROUP",
                "isPublic": True,
                "members": ["dgray", "jgray"],
            }),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get("name") == "F!RST GROUP", r.text
    assert r.json().get("isPublic") == True, r.text
    assert r.json().get("members") == ["dgray", "jgray"], r.text
    gid = r.json().get("id")
    created_time = r.json().get("createdTime")
    assert gid != None
    assert created_time != None

    # GET /groups
    r = s.get(BASE_URL + "/groups", headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get("groups") == [{
        "name": "F!RST GROUP",
        "isPublic": True,
        "members": ["dgray", "jgray"],
        "id": gid,
        "createdTime": created_time,
    }], r.text

    # GET /groups/gid not found
    r = s.get(BASE_URL + "/groups/nonexistent", headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # GET /groups/gid no auth
    r = s.get(BASE_URL + "/groups/" + gid)
    assert r.status_code == 401, r

    # GET /groups/gid
    r = s.get(BASE_URL + "/groups/" + gid, headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json() == {
        "name": "F!RST GROUP",
        "isPublic": True,
        "members": ["dgray", "jgray"],
        "id": gid,
        "createdTime": created_time,
    }, r.text

    # GET /groups/gid public
    r = s.get(BASE_URL + "/groups/" + gid, headers=GGRAY_AUTH)
    assert r.ok, r
    assert r.json() == {
        "name": "F!RST GROUP",
        "isPublic": True,
        "members": ["dgray", "jgray"],
        "id": gid,
        "createdTime": created_time,
    }, r.text

    # PUT /groups/gid no auth
    r = s.put(BASE_URL + "/groups/" + gid,
            json.dumps({
                "isPublic": False,
            }))
    assert r.status_code == 401, r

    # PUT /groups/gid not found
    r = s.put(BASE_URL + "/groups/nonexistent",
            json.dumps({
                "isPublic": False,
            }),
            headers=GGRAY_AUTH)
    assert r.status_code == 404, r

    # PUT /groups/gid
    r = s.put(BASE_URL + "/groups/" + gid,
            json.dumps({
                "isPublic": False,
            }),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json() == {
        "name": "F!RST GROUP",
        "isPublic": False,
        "members": ["dgray", "jgray"],
        "id": gid,
        "createdTime": created_time,
    }, r.text

    # PUT /groups/gid add member
    r = s.put(BASE_URL + "/groups/" + gid,
            json.dumps({
                "members": ["dgray", "ggray", "jgray"],
            }),
            headers=JGRAY_AUTH)
    assert r.ok, r
    r = s.get(BASE_URL + "/groups/" + gid, headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["members"] == ["dgray", "ggray", "jgray"], r.text

    # PUT /groups/gid remove member
    r = s.put(BASE_URL + "/groups/" + gid,
            json.dumps({
                "members": ["dgray", "jgray"],
            }),
            headers=JGRAY_AUTH)
    assert r.ok, r
    r = s.get(BASE_URL + "/groups/" + gid, headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["members"] == ["dgray", "jgray"]

    # PUT /groups/gid non-member
    r = s.put(BASE_URL + "/groups/" + gid,
            json.dumps({
                "isPublic": True,
            }),
            headers=GGRAY_AUTH)
    assert r.status_code == 403, r

    # DELETE /groups/gid/members/uid no auth
    r = s.delete("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"))
    assert r.status_code == 401, r

    # DELETE /groups/gid/members/uid non-member
    r = s.delete("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"),
            headers=GGRAY_AUTH)
    assert r.status_code == 403, r

    # DELETE /groups/gid/members/uid user not found
    r = s.delete("{}/groups/{}/members/{}".format(BASE_URL, gid, "ggray"),
            headers=JGRAY_AUTH)
    assert r.ok, r

    # DELETE /groups/gid/members/uid
    r = s.delete("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"),
            headers=JGRAY_AUTH)
    assert r.ok, r

    # GET /groups/gid/members deleted member
    r = s.get("{}/groups/{}/members".format(BASE_URL, gid),
            headers=DGRAY_AUTH)
    assert r.status_code == 403, r

    # GET /groups/gid/members
    r = s.get("{}/groups/{}/members".format(BASE_URL, gid),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get("ids") == ["jgray"], r.text

    # PUT /groups/gid/members/uid no auth
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"))
    assert r.status_code == 401, r

    # PUT /groups/gid/members/uid non-member
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"), headers=GGRAY_AUTH)
    assert r.status_code == 403, r

    # PUT /groups/gid/members/uid user not found
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, "nobody"), headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # PUT /groups/gid/members/uid
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r

    # GET /groups/gid/members
    r = s.get("{}/groups/{}/members".format(BASE_URL, gid), headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get("ids") == ["dgray", "jgray"], r.text

    # POST /users/uid/messages no body
    r = s.post("{}/users/{}/messages".format(BASE_URL, "dgray"), headers=JGRAY_AUTH)
    assert r.status_code == 400, r

    # POST /users/uid/messages no auth
    r = s.post("{}/users/{}/messages".format(BASE_URL, "dgray"),
            json.dumps({"body": "f!rst p0st"}))
    assert r.status_code == 401, r

    # POST /users/uid/messages user not found
    r = s.post("{}/users/{}/messages".format(BASE_URL, "nobody"),
            json.dumps({"body": "f!rst p0st"}),
            headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # POST /users/uid/messages
    r = s.post("{}/users/{}/messages".format(BASE_URL, "dgray"),
            json.dumps({"body": "f!rst p0st"}),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get('id') != None, r.text
    assert r.json().get('time') != None, r.text
    assert r.json().get('body') == "f!rst p0st", r.text
    assert r.json().get('from') == "jgray", r.text
    assert r.json().get('to') == "dgray", r.text

    # POST /users/uid/messages response
    r = s.post("{}/users/{}/messages".format(BASE_URL, "jgray"),
            json.dumps({"body": "bruh"}),
            headers=DGRAY_AUTH)
    assert r.ok, r

    # GET /users/uid/messages no auth
    r = s.get("{}/users/{}/messages".format(BASE_URL, "dgray"))
    assert r.status_code == 401, r

    # GET /users/uid/messages not found
    r = s.get("{}/users/{}/messages".format(BASE_URL, "nobody"), headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # GET /users/uid/messages
    r = s.get("{}/users/{}/messages".format(BASE_URL, "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert type(r.json().get('messages')) == list, r.text
    assert r.json()['messages'][0].get('body') == "f!rst p0st", r.text
    assert r.json()['messages'][1].get('body') == "bruh", r.text
    mid = r.json()['messages'][1]['id']

    # GET /users/uid/messages empty list
    r = s.get("{}/users/{}/messages".format(BASE_URL, "dgray"), headers=GGRAY_AUTH)
    assert r.ok, r
    assert r.json().get('messages') == [], r.text

    # POST /groups/gid/messages no auth
    r = s.post("{}/groups/{}/messages".format(BASE_URL, gid),
            json.dumps({"body": "w00p de gr00p"}))
    assert r.status_code == 401, r

    # POST /groups/gid/messages non-member
    r = s.post("{}/groups/{}/messages".format(BASE_URL, gid),
            json.dumps({"body": "w00p de gr00p"}),
            headers=GGRAY_AUTH)
    assert r.status_code == 403, r

    # POST /groups/gid/messages not a group
    r = s.post("{}/groups/{}/messages".format(BASE_URL, "not-a-group"),
            json.dumps({"body": "w00p de gr00p"}),
            headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # POST /groups/gid/messages
    r = s.post("{}/groups/{}/messages".format(BASE_URL, gid),
            json.dumps({"body": "w00p de gr00p"}),
            headers=JGRAY_AUTH)
    assert r.ok, r
    group_mid = r.json()['id']
    assert r.json().get('time') != None, r.text
    assert r.json().get('body') == "w00p de gr00p", r.text
    assert r.json().get('from') == "jgray", r.text
    assert r.json().get('to') == gid, r.text

    # GET /groups/gid/messages no auth
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid))

    # GET /groups/gid/messages non-member
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=GGRAY_AUTH)

    # GET /groups/gid/messages not a group
    r = s.get("{}/groups/{}/messages".format(BASE_URL, "not-a-group"), headers=DGRAY_AUTH)

    # GET /groups/gid/messages
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=DGRAY_AUTH)
    assert r.ok, r
    assert type(r.json().get('messages')) == list, r.text
    assert r.json()['messages'][0].get('from') == 'jgray', r.text

    # GET /users/uid/avatar no auth
    r = s.get("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            headers={"Accept": "application/octet-stream"})
    assert r.status_code == 401, r

    # GET /users/uid/avatar user not found
    r = s.get("{}/users/{}/avatar?authorization=jgray".format(BASE_URL, "nobody"),
            headers={"Accept": "application/octet-stream"})
    assert r.status_code == 404, r

    # GET /users/uid/avatar no avatar
    r = s.get("{}/users/{}/avatar?authorization=jgray".format(BASE_URL, "jgray"),
            headers={"Accept": "application/octet-stream"})
    assert r.status_code == 404, r

    # PUT /users/uid/avatar no auth
    r = s.put("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            RED_IMG_DATA,
            headers={"Content-Type": "application/octet-stream"})
    assert r.status_code == 401, r

    # PUT /users/uid/avatar wrong user
    r = s.put("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            RED_IMG_DATA,
            headers={
                "Authorization": "Bearer dgray",
                "Content-Type": "application/octet-stream",
            })
    assert r.status_code == 403, r

    # PUT /users/uid/avatar too large
    # FIXME: this is failing with a broken pipe for some reason
    # r = s.put("{}/users/{}/avatar".format(BASE_URL, "jgray"),
    #         "lots of bytes! " * 1000 * 1000,
    #         headers={
    #             "Authorization": "Bearer jgray",
    #             "Content-Type": "application/octet-stream",
    #         })
    # assert r.status_code == 413, r

    # PUT /users/uid/avatar
    r = s.put("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            RED_IMG_DATA,
            headers={
                "Authorization": "Bearer jgray",
                "Content-Type": "application/octet-stream",
            })
    assert r.ok, r

    # GET /users/uid should have avatarPath
    r = s.get("{}/users/{}".format(BASE_URL, "jgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()["avatarPath"] == "/users/jgray/avatar", r.text

    # GET /users/uid/avatar
    r = s.get("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            headers={
                "Authorization": "Bearer dgray",
                "Accept": "application/octet-stream",
            })
    assert r.ok, r
    assert r.content == RED_IMG_DATA, r.content

    # GET /users/uid/avatar with query param
    r = s.get("{}/users/{}/avatar".format(BASE_URL, "jgray"),
            headers={"Accept": "application/octet-stream"},
            params={"authorization": "dgray"})
    assert r.ok, r
    assert r.content == RED_IMG_DATA, r.content

    # GET /users/uid/receipts no auth
    r = s.get("{}/users/{}/receipts".format(BASE_URL, "dgray"))
    assert r.status_code == 401, r

    # GET /users/uid/receipts unknown user
    r = s.get("{}/users/{}/receipts".format(BASE_URL, "nobody"), headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # GET /users/uid/receipts no receipts
    r = s.get("{}/users/{}/receipts".format(BASE_URL, "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()['lastRead'] == [], r.text

    # POST /users/uid/receipts missing key
    r = s.post("{}/users/{}/receipts".format(BASE_URL, "dgray"),
            json.dumps({}),
            headers=JGRAY_AUTH)
    assert r.status_code == 400, r

    # POST /users/uid/receipts no auth
    r = s.post("{}/users/{}/receipts".format(BASE_URL, "dgray"),
            json.dumps({'messageId': mid}))
    assert r.status_code == 401, r

    # POST /users/uid/receipts message id not found in convo
    r = s.post("{}/users/{}/receipts".format(BASE_URL, "ggray"),
            json.dumps({'messageId': mid}),
            headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # POST /users/uid/receipts
    r = s.post("{}/users/{}/receipts".format(BASE_URL, "dgray"),
            json.dumps({'messageId': mid}),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get('time') != None
    r = s.post("{}/users/{}/receipts".format(BASE_URL, "jgray"),
            json.dumps({'messageId': mid}),
            headers=DGRAY_AUTH)
    assert r.ok, r

    # GET /users/uid/receipts
    r = s.get("{}/users/{}/receipts".format(BASE_URL, "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['lastRead']) == 2, r.text
    assert r.json()['lastRead'][0]['userId'] == 'jgray', r.text
    assert r.json()['lastRead'][0]['messageId'] == mid, r.text
    assert r.json()['lastRead'][1]['userId'] == 'dgray', r.text
    assert r.json()['lastRead'][1]['messageId'] == mid, r.text

    # GET /groups/gid/receipts non-member
    r = s.get("{}/groups/{}/receipts".format(BASE_URL, gid), headers=GGRAY_AUTH)
    assert r.status_code == 403

    # GET /groups/gid/receipts no group
    r = s.get("{}/groups/{}/receipts".format(BASE_URL, "not-a-group"), headers=JGRAY_AUTH)
    assert r.status_code == 404

    # GET /groups/gid/receipts no receipts
    r = s.get("{}/groups/{}/receipts".format(BASE_URL, gid), headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json()['lastRead'] == []

    # POST /groups/gid/receipts missing key
    r = s.post("{}/groups/{}/receipts".format(BASE_URL, gid),
            json.dumps({}),
            headers=JGRAY_AUTH)
    assert r.status_code == 400

    # POST /groups/gid/receipts non-member
    r = s.post("{}/groups/{}/receipts".format(BASE_URL, gid),
            json.dumps({'messageId': group_mid}),
            headers=GGRAY_AUTH)
    assert r.status_code == 403

    # POST /groups/gid/receipts message not in convo
    r = s.post("{}/groups/{}/receipts".format(BASE_URL, gid),
            json.dumps({'messageId': mid}),
            headers=JGRAY_AUTH)
    assert r.status_code == 404

    # POST /groups/gid/receipts
    r = s.post("{}/groups/{}/receipts".format(BASE_URL, gid),
            json.dumps({'messageId': group_mid}),
            headers=JGRAY_AUTH)
    assert r.ok, r
    assert r.json().get('messageId') == group_mid, r.text
    assert r.json().get('time') != None, r.text
    r = s.post("{}/groups/{}/receipts".format(BASE_URL, gid),
            json.dumps({'messageId': group_mid}),
            headers=DGRAY_AUTH)

    # GET /groups/gid/receipts
    r = s.get("{}/groups/{}/receipts".format(BASE_URL, gid), headers=JGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['lastRead']) == 2

    # PUT /users/{uid}/pinned/{uid} forbidden
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "dgray", "ggray"), headers=JGRAY_AUTH)
    assert r.status_code == 403, r

    # PUT /users/{uid}/pinned/{uid}
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r

    # PUT /users/{uid}/pinned/{uid} again for idempotency
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", "dgray"), headers=JGRAY_AUTH)
    assert r.ok, r

    # PUT /users/{uid}/pinned/{gid} non-member
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "ggray", gid), headers=JGRAY_AUTH)
    assert r.status_code == 403, r

    # PUT /users/{uid}/pinned/{gid} non-existent
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", "not-a-group"), headers=JGRAY_AUTH)
    assert r.status_code == 404, r

    # PUT /users/{uid}/pinned/{gid}
    r = s.put("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", gid), headers=JGRAY_AUTH)
    assert r.ok, r

    # GET /users/{uid}/pinned forbidden
    r = s.get("{}/users/{}/pinned".format(BASE_URL, "dgray"), headers=JGRAY_AUTH)
    assert r.status_code == 403, r

    # GET /users/{uid}/pinned
    r = s.get("{}/users/{}/pinned".format(BASE_URL, "jgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['ids']) == 2, r.text

    # DELETE /users/{uid}/pinned/{gid} forbidden
    r = s.delete("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", gid),
                headers={"Authorization": "Bearer jgray"})
    assert r.ok, r

    # DELETE /users/{uid}/pinned/{gid}
    r = s.delete("{}/users/{}/pinned/{}".format(BASE_URL, "jgray", gid), headers=JGRAY_AUTH)
    assert r.ok, r

    # GET /users/{uid}/pinned
    r = s.get("{}/users/{}/pinned".format(BASE_URL, "jgray"), headers=JGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['ids']) == 1, r.text

    # GET group member history new group
    r = s.post("{}/groups".format(BASE_URL), json.dumps({
            "name": "shoop de groop",
            "isPublic": True,
            "members": ["dgray", "jgray"],
        }),
        headers=JGRAY_AUTH)
    assert r.ok, r
    group = r.json()
    gid = group['id']
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=JGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['messages']) == 2, r.text
    assert r.json()['messages'][0]['isData'] == True
    assert r.json()['messages'][1]['isData'] == True
    data = [
        json.loads(r.json()['messages'][0]['body']),
        json.loads(r.json()['messages'][1]['body'])
    ]
    assert data[0]['type'] == 'MEMBER_ADDED', r.text
    assert data[1]['type'] == 'MEMBER_ADDED', r.text
    assert r.json()['messages'][0]['time'] == r.json()['messages'][1]['time'], r.text
    assert r.json()['messages'][0]['from'] == 'jgray', r.text

    # GET group member history kicked member
    r = s.delete("{}/groups/{}/members/{}".format(BASE_URL, gid, 'jgray'), headers=DGRAY_AUTH)
    assert r.ok
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=DGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['messages']) == 3, r.text
    assert r.json()['messages'][2]['isData'] == True
    assert json.loads(r.json()['messages'][2]['body'])['type'] == 'MEMBER_REMOVED', r.text
    assert json.loads(r.json()['messages'][2]['body'])['userId'] == 'jgray', r.text
    assert r.json()['messages'][2]['from'] == 'dgray', r.text

    # GET group member history added member
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, 'jgray'), headers=DGRAY_AUTH)
    assert r.ok
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=DGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['messages']) == 4, r.text
    assert r.json()['messages'][3]['isData'] == True
    assert json.loads(r.json()['messages'][3]['body'])['type'] == 'MEMBER_ADDED', r.text
    assert json.loads(r.json()['messages'][3]['body'])['userId'] == 'jgray', r.text
    assert r.json()['messages'][3]['from'] == 'dgray', r.text

    # GET group member history added member no-op
    r = s.put("{}/groups/{}/members/{}".format(BASE_URL, gid, 'jgray'), headers=DGRAY_AUTH)
    assert r.ok
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=DGRAY_AUTH)
    assert r.ok, r
    assert len(r.json()['messages']) == 4, r.text

    # GET group member history edited by PUT /groups/{gid}
    group['members'] = ['jgray', 'ggray']
    r = s.put("{}/groups/{}".format(BASE_URL, gid), json.dumps(group), headers=JGRAY_AUTH)
    assert r.ok
    r = s.get("{}/groups/{}/messages".format(BASE_URL, gid), headers=DGRAY_AUTH)
    assert r.ok, r
    assert r.json()['messages'][4]['isData'] == True
    assert r.json()['messages'][5]['isData'] == True
    assert len(r.json()['messages']) == 6, r.text
    assert r.json()['messages'][4]['time'] == r.json()['messages'][5]['time']
    assert json.loads(r.json()['messages'][4]['body'])['type'] != json.loads(r.json()['messages'][5]['body'])['type'] # one added, one removed

    # POST  /commands
    data = {
      "command": "diablo2asd",
      "method": "POST",
      "url": "https://test_url",
      "token" : "asdo1023kadl",
      "syntax" : "testcommand <int> <string>",
      "description" : "this is a test command",
    }
 
    r = s.post(BASE_URL + "/commands",
          json.dumps(data),
        headers=JGRAY_AUTH)
    assert r, r.ok

    # GET /commands
    r = s.get(BASE_URL + "/commands", headers=JGRAY_AUTH)
    assert r, r.ok
    assert r.json()['commands'][0]['command'] == data['command']
    assert r.json()['commands'][0]['method'] == data['method']

    print 'all tests passed!'
finally:
    print 'terminating sloth server'
    sloth_server.terminate()


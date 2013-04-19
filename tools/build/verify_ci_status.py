#!/usr/bin/env python

import requests
import subprocess
import sys

BASE_CI_URL = "https://ci.local:8543"

current_sha = subprocess.check_output("git log --pretty='%H' -n 1", shell=True).strip()

git_status = subprocess.check_output("git status --porcelain", shell=True).strip()

if git_status != "":
    print "Working directory is not clean. Please stash changes before deploying."
    #exit(1)

print "Current commit: {0}".format(current_sha)
sys.stdout.flush()

# get a list of last 20 successful builds
url = BASE_CI_URL + "/httpAuth/app/rest/buildTypes/id:bt17/builds"
params = {
    "status":"SUCCESS",
    "count":"20"
}
headers = {
    'accept': 'application/json'
}
# This is a non admin account. If we cared about it we wouldn't set the
# password to temp123.
auth = ('aerofsteam','temp123')
r = requests.get(url, params=params, auth=auth, headers=headers, verify=False)
builds = r.json()["build"]

for build in builds:
    # for each build we need to make an api call for more details
    build_url = BASE_CI_URL + build["href"]
    build_request = requests.get(build_url, auth=('aerofsteam','temp123'), headers=headers, verify=False)
    build_info = build_request.json()
    # grab the sha of the build and compare with the current sha
    sha = build_info["revisions"]["revision"][0]["version"]
    if current_sha == sha:
        print "Current commit successfully passed CI"
        exit(0)

print "Current commit has not passed CI"
exit(1)

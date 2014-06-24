#!env/bin/python
import datetime
import hashlib
import json
import urllib
import re

import requests
from flask import Flask, request, Response

from aerofs_common.configuration import Configuration

##### taken from Drew's secutil.py
alpha = [c for c in "abcdefghijklmnop"]
def alphabet_encode(bytestring):
    if not isinstance(bytestring, bytes):
        raise ValueError("bytestring must be a byte array")
    retval = []
    for c in bytestring:
        b = ord(c)
        retval.append(alpha[(b >> 4) & 0xf])
        retval.append(alpha[b & 0xf])
    return "".join(retval)

def magichash(username, did):
    if not isinstance(username, unicode):
        raise ValueError("username must be a unicode string")
    if not isinstance(did, bytes):
        raise ValueError("did must be a byte array")
    h = hashlib.sha256()
    h.update(username.encode('utf-8'))
    h.update(did)
    return h.digest()
##### end of secutil stuff

app = Flask(__name__)

# Include additional configuration from deployment-specific config.py
app.config.from_object('config')

# This currently uses client properties because we haven't finished working out
# server properties for hybrid cloud.
aerofs_config = Configuration(app.config["CONFIG_SERVER_BASE_URI"]).client_properties()

PATTERN = r"Aero-Device-Cert ([0-9a-fA-F]{32}) (.*)"
AUTH_HEADER_REGEX = re.compile(PATTERN)

# Compute verkehr base URI from configuration retrieved
VERKEHR_SCHEME_HOST_PORT = "http://{}:{}".format(
        aerofs_config["base.verkehr.host"],
        aerofs_config["base.verkehr.port.rest"],
        )
TOPICS_PATH = "/v2/topics"

@app.route("/checkin", methods=["POST"])
def checkin():
    # check that nginx thinks we sent this with a valid cert
    if not request.headers["Verify"] == "SUCCESS":
        app.logger.info("invalid cert")
        return Response("No valid certificate provided", 401)
    dname = request.headers["DName"]
    cname = None
    for part in dname.split("/"):
        if part.startswith("CN="):
            cname = part[3:]
            break
    if not cname:
        app.logger.error("cname missing from certificate")
        return Response("cname not present in certificate", 401)

    # check that the user provided a username/did in the authorization header
    auth_header = request.headers["Authorization"]
    match = AUTH_HEADER_REGEX.match(auth_header)
    if not match:
        app.logger.info("Authorization header malformed: {}".format(auth_header))
        return Response("Authorization header must match {}".format(PATTERN), 401)
    did = match.group(1)
    did_raw = did.decode('hex')
    userid = match.group(2)

    # check that the username/did pair matches that expected for this certificate cname
    expected_cname = alphabet_encode(magichash(userid, did_raw))
    if cname != expected_cname:
        app.logger.info("Invalid cname for {}:{} - expected {}, got {}".format(
            userid, did, expected_cname, cname))
        return Response("Certificate cname did not match expected cname for userid/did", 401)

    # OK, request is authenticated.
    # Do a post to the appropriate verkehr topic for this device.
    topic = "online/{}".format(did)
    topic_path = "{}/{}".format(TOPICS_PATH, urllib.quote(topic, safe=""))
    topic_url = "{}{}".format(VERKEHR_SCHEME_HOST_PORT, topic_path)
    payload = {
        "ip": request.remote_addr,
        "time": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    r = requests.post(topic_url, data=json.dumps(payload))
    if not r.ok:
        app.logger.warn("{}: failed to post to Verkehr REST API", did)
        return Response("Failed to post to Verkehr REST API", 500)

    # Everything went fine.
    app.logger.info("{}:{} checked in from {}".format(userid, did, request.remote_addr))
    return "Check-in successful\n"

# A simple healthcheck URL that just says "yeah, I'm up and running"
@app.route("/healthcheck")
def healthcheck():
    return "OK\n"

if __name__ == "__main__":
    app.run(debug=True)

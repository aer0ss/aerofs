#!env/bin/python
import base64
import datetime
import hashlib
import json
import os
import re
import urllib

from tornado.httpclient import AsyncHTTPClient, HTTPRequest
import tornado.httpserver
from tornado.httputil import HTTPHeaders
import tornado.ioloop
from tornado.web import RequestHandler, Application, url, asynchronous, HTTPError

from aerofs_common.configuration import Configuration

_pwd = os.path.dirname( os.path.realpath( __file__ ) )

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

LEGACY_PATTERN = r"Aero-Device-Cert ([0-9a-fA-F]{32}) (.*)"
PATTERN = r"Aero-Device-Cert ([0-9a-zA-Z\+/=]+) ([0-9a-fA-F]{32})"
LEGACY_AUTH_HEADER_REGEX = re.compile(LEGACY_PATTERN)
AUTH_HEADER_REGEX = re.compile(PATTERN)

class CheckinHandler(RequestHandler):
    def initialize(self, config):
        # Compute verkehr base URI from configuration retrieved
        self.VERKEHR_SCHEME_HOST_PORT = "http://{}:{}".format(
            config["base.verkehr.host"],
            config["base.verkehr.port.rest"],
        )
        self.TOPICS_PATH = "/v2/topics"

    def write_error(self, status_code, **kwargs):
        self.set_status(status_code)
        self.set_header("Content-Type", "text/plain")
        message = "Error {}\n".format(status_code)
        if "exc_info" in kwargs:
            # Standard exception info triple
            zero, one, two = kwargs["exc_info"]
            # Send client the same message we log
            if hasattr(one, "log_message"):
                message = one.log_message
        self.write(message)
        self.finish()

    @asynchronous
    def post(self):
        if "Verify" not in self.request.headers or self.request.headers["Verify"] != "SUCCESS":
            raise HTTPError(401, "Expected Verify header.\n")

        if "DName" not in self.request.headers:
            raise HTTPError(401, "Expected DName header.\n")
        dname = self.request.headers["DName"]
        cname = None
        for part in dname.split("/"):
            if part.startswith("CN="):
                cname = part[3:]
                break
        if not cname:
            raise HTTPError(401, "Expected certificate to have a cname.\n")

        # check that the user provided a username/did in the authorization header
        if "Authorization" not in self.request.headers:
            raise HTTPError(401, "Expected Authorization header.\n")
        auth_header = self.request.headers["Authorization"]

        userid = None
        did_raw = None

        match = AUTH_HEADER_REGEX.match(auth_header)
        if match:
            userid_base64 = match.group(1)
            userid = userid_base64.decode('base64')
            did = match.group(2)
            did_raw = did.decode('hex')
        else:
            # possibly an older client - try the LEGACY_AUTH_HEADER_REGEX format
            legacy_match = LEGACY_AUTH_HEADER_REGEX.match(auth_header)
            if legacy_match:
                did = legacy_match.group(1)
                did_raw = did.decode('hex')
                userid = legacy_match.group(2)
            else:
                raise HTTPError(401, "Authorization header must match {}\n".format(PATTERN))

        # check that the username/did pair matches that expected for this certificate cname
        expected_cname = alphabet_encode(magichash(userid.decode('utf-8'), did_raw))
        if cname != expected_cname:
            log_msg = "Invalid cname for {}:{} - expected {}, got {}".format(
                    userid, did, expected_cname, cname)
            raise HTTPError(401, log_msg)

        # OK, request is authenticated.
        # Do a post to the appropriate verkehr topic for this device.
        topic = "online/{}".format(did)
        topic_path = "{}/{}".format(self.TOPICS_PATH, urllib.quote(topic, safe=""))
        topic_url = "{}{}".format(self.VERKEHR_SCHEME_HOST_PORT, topic_path)
        payload = {
            "ip": self.request.remote_ip,
            "time": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        }

        # Load shared secret from disk
        with open("/data/deployment_secret") as f:
            deployment_secret = f.read().strip()
        auth_header = "Aero-Delegated-User-Device charlie {} {} {}".format(
                deployment_secret, base64.b64encode(userid), did_raw.encode('hex')
        )

        http_client = AsyncHTTPClient()
        headers = HTTPHeaders({
            "Content-Type": "application/octet-stream",
            "Authorization": auth_header,
        })
        request = HTTPRequest(topic_url, headers=headers, method="POST", body=json.dumps(payload))
        # Send request, continuation will send a reply to the requester
        http_client.fetch(request, self.on_verkehr_response)

    def on_verkehr_response(self, response):
        if response.error:
            print "Error:", response.error
            raise HTTPError(500, "Internal error\n")
        else:
            self.set_status(200)
            self.set_header("Content-Type", "text/plain")
            self.write("Check-in successful.\n")
            self.finish()

# A simple healthcheck URL that just says "yeah, I'm up and running"
class HealthcheckHandler(RequestHandler):
    def head(self):
        """The sanity check uses HEAD to check for liveness, so implement that."""
        self.set_status(200)
        self.write("")
        self.finish()

    def get(self):
        self.write("OK\n")
        self.finish()

def make_app(config):
    return Application([
        url(r"/checkin", CheckinHandler, {"config": config}),
        url(r"/healthcheck", HealthcheckHandler),
        ])

if __name__ == "__main__":
    # Include additional configuration from deployment-specific config.py
    config = {}
    execfile(os.path.join(_pwd, 'config.py'), globals(), config)
    print config

    # This currently uses client properties because we haven't finished working out
    # server properties for hybrid cloud.
    config_client = None
    if "CONFIG_SERVER_CERT" in config:
        config_client = Configuration(config["CONFIG_SERVER_BASE_URI"],
                                      custom_cert=config["CONFIG_SERVER_CERT"],
                                      service_name='charlie')
    else:
        config_client = Configuration(config["CONFIG_SERVER_BASE_URI"],
                                      service_name='charlie')
    aerofs_config = config_client.client_properties()

    port = 8701
    app = make_app(aerofs_config)
    # set xheaders=True to trust X-Forwarded-For, X-Real-IP, etc.
    http_server = tornado.httpserver.HTTPServer(app, xheaders=True)
    http_server.listen(port)
    print "Starting up charlie on port {}".format(port)
    tornado.ioloop.IOLoop.instance().start()

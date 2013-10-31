from flask import Flask, render_template, request, Response, make_response
from functools import wraps
from aerofs_licensing import license_file
from io import BytesIO
import base64
import datetime
import errno
import hashlib
import os
import re
import tempfile

# ----------------------------------------------------------------------
# Statics
# ----------------------------------------------------------------------

PROPERTIES_EXTERNAL = "/opt/config/properties/external.properties"
PROPERTIES_EXTERNAL_TMP = "/opt/config/properties/external.properties.tmp"
CACERT_PATH = "/etc/ssl/certs/AeroFS_CA.pem"

LICENSE_FILE_PATH = "/etc/aerofs/license.gpg"

app = Flask(__name__)
app.config.from_object(__name__)

# ----------------------------------------------------------------------
# Utils
# ----------------------------------------------------------------------

# Compares two bytestrings in constant time.
def const_time_is_equal(x, y):
    if len(x) != len(y):
        return False
    result = 0
    for a, b in zip(x, y):
        result |= ord(a) ^ ord(b)
    return result == 0

# Decorator to require that the client knows the sha1sum of the current license file
def require_matching_license_shasum():
    def wrapper(f):
        @wraps(f)
        def wrapped(*args, **kwargs):
            if not os.path.exists(LICENSE_FILE_PATH):
                return Response("No license file on record for this appliance to auth against.\n", status=500)
            purported_sha1 = request.args.get("license_sha1")
            if not purported_sha1:
                return Response("Expected param `license_sha1` but none was given.\n", status=400)
            if not re.match("[0-9A-Fa-f]{40}", purported_sha1):
                return Response("license_sha1 was not a 40-char hex digest.\n", status=400)
            with open(LICENSE_FILE_PATH) as license_file:
                license_data = license_file.read()
            actual_shasum = hashlib.sha1(license_data).hexdigest()
            if not const_time_is_equal(purported_sha1.lower(), actual_shasum):
                return Response("license_sha1 does not match that of the current license file.\n", status=400)
            return f(*args, **kwargs)
        return wrapped
    return wrapper

# Decorator to set the Content-type header to "text/plain; charset=utf-8"
def returns_plaintext(f):
    @wraps(f)
    def wrapped(*args, **kwargs):
        rv = make_response(f(*args, **kwargs))
        rv.mimetype = "text/plain"
        return rv
    return wrapped

def mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise

def read_dict_from_file(filename):
    d = {}
    with open(filename, 'r') as f:
        for line in f:
            (key, val) = line.split('=', 1)
            d[key] = val.strip()
    return d

def write_dict_to_file(d, tmpfilename, dstfilename):
    with open(tmpfilename, 'w') as f:
        for key in d.keys():
            f.write(str(key) + "=" + str(d[key]) + '\n')
    os.rename(tmpfilename, dstfilename)

def get_external_variables():
    return read_dict_from_file(PROPERTIES_EXTERNAL)

def get_ca_cert_as_dict():
    d = {}
    if os.path.exists(CACERT_PATH):
        with open(CACERT_PATH) as f:
            root_cert = f.read().replace("\n", "\\n")
            d["base_ca_cert"] = root_cert
    return d

def get_template_kv_pairs():
    # Get explicit user-provided properties
    d = get_external_variables()
    # Add CA cert, if available
    d = dict(d, **get_ca_cert_as_dict())
    # Add properties from license file
    d["license_lines"] = "\n".join([ "{}={}".format(k, v) for k, v in current_license_info.iteritems() ])
    return d

# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------

# No authentication, this route is public
@app.route("/client")
@returns_plaintext
def client_properties():
    return render_template("client.tmplt", **get_template_kv_pairs())

# TODO: Authentication (via license file shasum) required.
@app.route("/server")
@returns_plaintext
#@require_matching_license_shasum()
def server_properties():
    return render_template("server.tmplt", **get_template_kv_pairs())

@app.route("/set", methods=["POST"])
@returns_plaintext
#@require_matching_license_shasum()
def set_external_variable():
    key = request.form['key']
    value = request.form['value']
    d = read_dict_from_file(PROPERTIES_EXTERNAL)
    d[key] = value
    write_dict_to_file(d, PROPERTIES_EXTERNAL_TMP, PROPERTIES_EXTERNAL)
    return ""

# No authentication required
@app.route("/check_license_sha1", methods=["POST"])
@returns_plaintext
@require_matching_license_shasum()
def check_license_sha1():
    """
    Check whether the given license sha1sum matches that of the
    currently-active license file.

    If this request returns 200, the shasum matched.
    If the request returns 400, the shasum was invalid
    If the request returns 500, there was no license file on record or another
    internal error occurred.
    """
    return Response("Correct sha1sum for current license.\n", status=200)

# No authentication required; this path does its own checks.
@app.route("/set_license_file", methods=["POST"])
@returns_plaintext
def set_license_file():
    """
    Apply the AeroFS-signed license file to the site.

    This path expects form parameter `license_file` to contain the
    urlsafe-base64-encoded bytestring.

    If this request returns 200, the license file was accepted.
    If this request returns any other code, then the license file was rejected.
    """
    license_base64 = request.form['license_file'].encode('latin1')
    try:
        license_file_bytes = base64.urlsafe_b64decode(license_base64)
    except Exception as e:
        return Response("License file was not a valid base64 string.\n", status=400)

    # If the license file is not signed by the AeroFS root key, reject.
    # Otherwise, load the license information into a LicenseInfo object
    # `new_license_info`, which is a dict with some bonus methods
    buf = BytesIO(license_file_bytes)
    try:
        new_license_info = license_file.verify_and_load(buf)
    except:
        return Response("License file was not signed by a trusted authority.\n", status=400)

    # If there already exists an installed license file, ensure that the new
    # one is issued to the same license_customer_id.
    if os.path.exists(LICENSE_FILE_PATH):
        # We ignore the possibility that the license file exists but is not
        # signed by the appropriate authority, since we expect to be the only
        # service writing to that file.
        old_license_file_bytes = None
        with open(LICENSE_FILE_PATH) as f:
            old_license_file_bytes = f.read()
        if license_file_bytes == old_license_file_bytes:
            return "Provided license file matches the currently-active license file.\n"
        old_license_file = BytesIO(old_license_file_bytes)
        old_license_info = license_file.verify_and_load(old_license_file)
        if old_license_info["customer_id"] != new_license_info["customer_id"]:
            return Response("New license file customer_id differs from that of existing license.\n", status=400)

    # Verify that if the license has an expiry date, that that date is
    # currently in the future (that is, that the new license is currently valid)
    if not new_license_info.is_currently_valid():
        return Response("New license file is no longer valid.\n", status=400)

    # The new license is okay.  Persist it.
    # Ensure license directory exists.
    mkdir_p(os.path.dirname(LICENSE_FILE_PATH))
    # Write the whole gpg-signed license data + signature to a file in the same
    # folder as the license file
    new_license_tempfile = tempfile.NamedTemporaryFile(prefix="new-license", dir=os.path.dirname(LICENSE_FILE_PATH), delete=False)
    new_license_tempfile_path = new_license_tempfile.name
    new_license_tempfile.write(license_file_bytes)
    new_license_tempfile.close()
    # Atomically rename the new file over the old one and replace the in-memory
    # config cache.
    global current_license_info
    current_license_info = new_license_info
    os.rename(new_license_tempfile_path, LICENSE_FILE_PATH)
    # return 200 OK
    return "License file accepted.\n"

# Load license info to current_license_info if it exists
# If it doesn't (or is invalid), leave current_license_info empty.
current_license_info = {}
try:
    with open(LICENSE_FILE_PATH) as f:
        current_license_info = license_file.verify_and_load(f)
except:
    # If there's no file, or if it's invalid, then we have no trustworthy
    # license information.
    pass

# ----------------------------------------------------------------------
# Main.  Only use this for internal testing; in production, use cherrypy
# or some other WSGI server that scales better.
# ----------------------------------------------------------------------

if __name__ == '__main__':
    try:
        app.run(host="127.0.0.1", port=5434)
    except KeyboardInterrupt:
        app.stop()

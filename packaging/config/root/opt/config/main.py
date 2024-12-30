from flask import Flask, render_template, request, Response, make_response
from functools import wraps
from aerofs_licensing import license_file
from io import BytesIO
import base64
import errno
import hashlib
import os
import re
import tempfile
import datetime

# ----------------------------------------------------------------------
# Statics
# ----------------------------------------------------------------------

PWD = os.path.abspath(os.path.dirname(__file__))
PROPERTIES_EXTERNAL = os.path.join(PWD, "properties", "external.properties")
PROPERTIES_EXTERNAL_TMP = os.path.join(PWD, "properties","external.properties.tmp")
MODIFIED_TIME_KEY = "properties_modification_time"
DEPLOYMENT_SECRET_PATH = "/data/deployment_secret"

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
                app.logger.debug("Requested path {} required authentication, but no license file on record to auth against".format(request.path))
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

# Decorator to require that the client provided an Authorization:
# Aero-Service-Shared-Secret header with the current deployment secret
def require_deployment_secret():
    def wrapper(f):
        @wraps(f)
        def wrapped(*args, **kwargs):
            auth_header = request.headers.get("Authorization", "")
            m = re.match(r"Aero-Service-Shared-Secret ([0-9A-Za-z_-]+) ([0-9A-Fa-f]+)", auth_header)
            if not m:
                return Response("Authorization header invalid.", status=400)
            purported_deployment_secret = m.group(2)
            actual_deployment_secret = None
            with open(DEPLOYMENT_SECRET_PATH, "r") as secret_file:
                actual_deployment_secret = secret_file.readline().strip()
            if not const_time_is_equal(purported_deployment_secret, actual_deployment_secret):
                return Response("Invalid deployment secret in Authorization header.", status=400)
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
            # ignore empty lines and comment lines for robustness to human changes
            if line == '\n' or line.startswith("#"):
                continue
            (key, val) = line.split('=', 1)
            d[key] = val.strip()
    return d

def write_dict_to_file(d, tmpfilename, dstfilename):
    with open(tmpfilename, 'w') as f:
        for key in list(d.keys()):
            f.write(str(key) + "=" + str(d[key]) + '\n')
    os.rename(tmpfilename, dstfilename)

def update_properties_file(props):
    d = read_dict_from_file(PROPERTIES_EXTERNAL)
    d.update(props)
    d[MODIFIED_TIME_KEY] = datetime.datetime.utcnow().isoformat()
    write_dict_to_file(d, PROPERTIES_EXTERNAL_TMP, PROPERTIES_EXTERNAL)

def get_template_kv_pairs():
    # Get explicit user-provided properties
    d = read_dict_from_file(PROPERTIES_EXTERNAL)

    # Add properties from license file
    d["license_lines"] = "\n".join([ "{}={}".format(k, v) for k, v in list(current_license_info.items()) ])
    for k, v in list(current_license_info.items()):
        d[k] = v
    return d

def get_port(port_name, default_value):
    v = os.environ.get('hpc_port_' + port_name)
    return default_value if v is None else int(v)


# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------

@app.before_first_request
def write_port_numbers():
    app.logger.info("Querying the loader for port numbers")

    # This list must be kept in sync with `crane.yml.jinja` (in the loader)
    ports = {'nginx': 4433,
             'nginx_mut_auth': 5222,
             'havre': 8084,
             'lipwig': 29438}

    results = {'{}_port'.format(name): get_port(name, default_value)
               for name, default_value in list(ports.items())}

    app.logger.info("Got ports: {}".format(results))
    update_properties_file(results)


# No authentication, this route is public
@app.route("/client")
@returns_plaintext
def client_properties():
    return render_template("client.tmplt", **get_template_kv_pairs())

# TODO: Authentication (via license file shasum) required.
@app.route("/server")
@returns_plaintext
@require_deployment_secret()
def server_properties():
    return render_template("server.tmplt", **get_template_kv_pairs())

@app.route("/set", methods=["POST"])
@returns_plaintext
@require_deployment_secret()
def set_external_variable():
    key = request.form['key']
    value = request.form['value']
    update_properties_file({key: value})
    return ""

# No authentication required
@app.route("/check_license_sha1", methods=["GET"])
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

@app.route("/is_license_valid", methods=["GET"])
@returns_plaintext
def is_license_valid():
    """
    Returns content body "1" if the license is currently valid. "0" otherwise.
    """
    try:
        if license_file.verify_and_load(open(LICENSE_FILE_PATH)).is_currently_valid():
            return '1'
    except:
        pass
    return '0'

# No authentication required; this path does its own checks.
@app.route("/set_license_file", methods=["POST"])
@returns_plaintext
def set_license_file():
    """
    Apply the AeroFS-signed license file to the site.

    This path expects form parameter `license_file` to contain the
    urlsafe-base64-encoded bytestring.

    If this request returns 200, the license file was accepted, and the license-expired-flag file is
    removed.
    If this request returns any other code, then the license file was rejected.
    """
    license_base64 = request.form['license_file'].encode('latin1')
    try:
        license_file_bytes = base64.urlsafe_b64decode(license_base64)
    except Exception as e:
        app.logger.info("Refusing non-base64'd license file")
        return Response("License file was not a valid base64 string.\n", status=400)

    # If the license file is not signed by the AeroFS root key, reject.
    # Otherwise, load the license information into a LicenseInfo object
    # `new_license_info`, which is a dict with some bonus methods
    buf = BytesIO(license_file_bytes)
    try:
        new_license_info = license_file.verify_and_load(buf)
    except:
        app.logger.info("Refusing unsigned license file")
        return Response("License file was not signed by a trusted authority.\n", status=400)

    # Verify that if the license has an expiry date, that that date is
    # currently in the future (that is, that the new license is currently valid)
    if not new_license_info.is_currently_valid():
        app.logger.info("Refusing new license that has already expired (expiry date: %s)",
                new_license_info["license_valid_until"])
        return Response("New license file is no longer valid.\n", status=400)

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
            app.logger.info("Refusing new license file with different customer_id (existing: %s, new: %s)",
                    old_license_info["customer_id"], new_license_info["customer_id"])
            return Response("New license file customer_id differs from that of existing license.\n", status=400)
        # We refuse to accept licenses that were issued before the current
        # license file was issued to prevent accidental or malicious license
        # rollback by an uncoordinated set of administrators.
        if old_license_info.issue_date() > new_license_info.issue_date():
            app.logger.info("Refusing new license file issued before the current license file (exising: %s, new %s)",
                    old_license_info.issue_date().isoformat(), new_license_info.issue_date().isoformat())
            return Response("New license file was issued before the current license file.\n" +
                            "Cowardly refusing to downgrade license.\n", status=400)

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
    app.logger.info("Set new license file for %s (id %s), valid until %s",
            new_license_info["license_company"],
            new_license_info["customer_id"],
            new_license_info["license_valid_until"])
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
        app.run(host="127.0.0.1", port=5434, debug=True)
    except KeyboardInterrupt:
        app.stop()

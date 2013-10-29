from flask import Flask, render_template, request, Response
from functools import wraps
import os

# ----------------------------------------------------------------------
# Statics
# ----------------------------------------------------------------------

PROPERTIES_EXTERNAL = "/opt/config/properties/external.properties"
PROPERTIES_EXTERNAL_TMP = "/opt/config/properties/external.properties.tmp"
CACERT_PATH = "/etc/ssl/certs/AeroFS_CA.pem"

app = Flask(__name__)
app.config.from_object(__name__)

# ----------------------------------------------------------------------
# Utils
# ----------------------------------------------------------------------

def returns_plaintext(f):
    @wraps(f)
    def wrapped(*args, **kwargs):
        r = f(*args, **kwargs)
        return Response(r, content_type='text/plain; charset=utf-8')
    return wrapped

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
    return d

# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------

@app.route("/client")
@returns_plaintext
def client_properties():
    return render_template("client.tmplt", **get_template_kv_pairs())

@app.route("/server")
@returns_plaintext
def server_properties():
    return render_template("server.tmplt", **get_template_kv_pairs())

@app.route("/set", methods=["POST"])
@returns_plaintext
def set_external_variable():
    key = request.form['key']
    value = request.form['value']
    d = read_dict_from_file(PROPERTIES_EXTERNAL)
    d[key] = value
    write_dict_to_file(d, PROPERTIES_EXTERNAL_TMP, PROPERTIES_EXTERNAL)
    return ""

# ----------------------------------------------------------------------
# Main.  Only use this for internal testing; in production, use cherrypy
# or some other WSGI server that scales better.
# ----------------------------------------------------------------------

if __name__ == '__main__':
    try:
        app.run(host="127.0.0.1", port=5434)
    except KeyboardInterrupt:
        app.stop()

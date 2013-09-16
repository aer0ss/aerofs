from flask import Flask, render_template, request
from os import rename

# ----------------------------------------------------------------------
# Statics
# ----------------------------------------------------------------------

PROPERTIES_EXTERNAL = "/opt/config/properties/external.properties"
PROPERTIES_EXTERNAL_TMP = "/opt/config/properties/external.properties.tmp"

app = Flask(__name__)
app.config.from_object(__name__)

# ----------------------------------------------------------------------
# Utils
# ----------------------------------------------------------------------

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
    rename(tmpfilename, dstfilename)

def get_external_variables():
    return read_dict_from_file(PROPERTIES_EXTERNAL)

# ----------------------------------------------------------------------
# Routes
# ----------------------------------------------------------------------

@app.route("/client")
def client_properties():
    return render_template("client.tmplt", **get_external_variables())

@app.route("/server")
def server_properties():
    return render_template("server.tmplt", **get_external_variables())

@app.route("/set", methods=["POST"])
def set_external_variable():
    key = request.form['key']
    value = request.form['value']
    d = read_dict_from_file(PROPERTIES_EXTERNAL)
    d[key] = value
    write_dict_to_file(d, PROPERTIES_EXTERNAL_TMP, PROPERTIES_EXTERNAL)
    return ""

# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------

if __name__ == '__main__':
    app.run(host="127.0.0.1", port=5434)

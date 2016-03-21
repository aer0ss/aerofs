import os
import uuid
import requests
from datetime import datetime
from OpenSSL import crypto
from setup_storage_agent import setup_storage_agent
from flask import Flask, request, abort, flash, render_template

SERVER_FILES_DIR = "/aerofs-storage/server"
SERVER_KEY_LOCATION = "/aerofs-storage/server/server.key"
SERVER_CERT_LOCATION = "/aerofs-storage/server/server.crt"
SERVER_SETUP_LOCATION = "/aerofs-storage/server/setup-completed"
STORAGE_BUNDLE_LOCATION = "/aerofs-storage/configbundle"

app = Flask("storage server", static_folder="/opt/storage-agent-setup", template_folder="/opt/storage-agent-setup")

@app.route('/', methods=["GET"])
def root():
    if os.path.exists(SERVER_SETUP_LOCATION):
        return app.send_static_file("upgrade.html")
    else:
        return render_template("configure.html", example_config_file_name=config_file_name())

def config_file_name():
    return '{}{}'.format("aerofs-storage-setup_",
                           datetime.today().strftime('%Y%m%d-%H%M%S'))

@app.route('/upload', methods=["POST"])
def upload():
    if os.path.exists(SERVER_SETUP_LOCATION):
        # cannot re-configure a running storage agent
        flash("Server Already Setup", category='error')
        abort(409)
    f = request.files.get("file", None)
    if not f:
        flash("No File Uploaded", category='error')
        abort(400)
    f.save(STORAGE_BUNDLE_LOCATION)
    flash("Uploaded Configuration")

    setup_storage_agent(STORAGE_BUNDLE_LOCATION)
    flash("Configured Storage Server")
    # mark server as setup
    with open(SERVER_SETUP_LOCATION, 'w') as _:
        pass
    return ""

@app.route('/json-boot', methods=["POST"])
def boot_to_target():
    if request.mimetype != "application/json":
        abort(415)
    target = request.get_json().get("target", None)
    if not target or target not in ["default", "maintenance"]:
        abort(400)
    r = requests.post("http://loader.service/v1/boot/default")
    r.raise_for_status()

boot_mode_cached = None
@app.route('/json-get-boot', methods=["GET"])
def get_boot():
    global boot_mode_cached
    if boot_mode_cached is None:
        r = requests.get("http://loader.service/v1/boot")
        r.raise_for_status()
        boot_mode_cached = r.json()
    return boot_mode_cached

def setup_key_cert():
    if os.path.exists(SERVER_CERT_LOCATION) and os.path.exists(SERVER_KEY_LOCATION):
        # already exist, no need to redo work
        return
    if not os.path.exists(SERVER_FILES_DIR):
        os.makedirs(SERVER_FILES_DIR)
    key = crypto.PKey()
    key.generate_key(crypto.TYPE_RSA, 2048)

    csr = new_csr(new_ca_cert_name(), key)
    cert = self_sign_csr(key, csr)
    write_key_to_file(key, SERVER_KEY_LOCATION)
    write_cert_to_file(cert, SERVER_CERT_LOCATION)

def new_ca_cert_name():
    return "AeroFS-Storage-" + str(uuid.uuid4())

def new_csr(cname, pkey):
    csr = crypto.X509Req()
    subj = csr.get_subject()
    subj.CN = cname
    subj.O = "aerofs.com"
    subj.ST = "CA"
    subj.C = "US"
    subj.L = "SF"
    subj.emailAddress = "support@aerofs.com"
    csr.set_pubkey(pkey)
    csr.sign(pkey, "sha1")

    return csr

def self_sign_csr(pkey, csr, serial=0, days=365):
    cert = crypto.X509()
    cert.set_subject(csr.get_subject())
    cert.set_serial_number(serial)
    cert.gmtime_adj_notBefore(0)
    # need to get the seconds for which the cert is valid
    cert.gmtime_adj_notAfter(days * 24 * 60 * 60)
    cert.set_issuer(csr.get_subject())
    cert.set_pubkey(csr.get_pubkey())
    cert.sign(pkey, "sha1")
    return cert

def write_key_to_file(key, file):
    with open(file, 'w') as f:
        f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))

def write_cert_to_file(cert, file):
    with open(file, 'w') as f:
        f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))

if __name__ == "__main__":
    setup_key_cert()
    app.secret_key = "secretkey"
    app.run("0.0.0.0", 443, ssl_context=(SERVER_CERT_LOCATION, SERVER_KEY_LOCATION))

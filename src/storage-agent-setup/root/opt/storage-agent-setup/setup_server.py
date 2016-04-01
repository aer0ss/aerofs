import os
import uuid
import requests
from distutils.version import LooseVersion
from datetime import datetime
from OpenSSL import crypto
from setup_storage_agent import setup_storage_agent
from flask import Flask, request, abort, flash, render_template, jsonify
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_common.exception import ExceptionReply

SERVER_FILES_DIR = "/aerofs-storage/server"
SERVER_KEY_LOCATION = "/aerofs-storage/server/server.key"
SERVER_CERT_LOCATION = "/aerofs-storage/server/server.crt"
SERVER_SETUP_LOCATION = "/aerofs-storage/server/setup-completed"
STORAGE_BUNDLE_LOCATION = "/aerofs-storage/configbundle"
STATIC_LOC = "/opt/storage-agent-setup/static"
app = Flask("storage server", static_folder=STATIC_LOC, template_folder=STATIC_LOC)

REG = "registry.aerofs.com"
LOADER_URL = 'http://loader.service/v1'
_version = None
boot_mode_cached = None
# 1 Gebi
disk_needed = 1073741824


def get_version():
    global _version
    # cache the value to avoid frequent reads
    if not _version:
        with open('/version/current.ver') as f:
            _, _version = f.readline().strip().split('=', 1)

    return _version


def config_file_name():
    return '{}{}'.format("aerofs-storage-setup_",
                         datetime.today().strftime('%Y%m%d-%H%M%S'))


def _latest_version_from_registry():
    r = requests.get("{}/tags/latest/{}".format(LOADER_URL, REG))
    r.raise_for_status()
    return str(r.json())


@app.route('/', methods=["GET"])
def root():
    if os.path.exists(SERVER_SETUP_LOCATION):
        return render_template("upgrade.html", version=get_version())
    else:
        return render_template("configure.html", example_config_file_name=config_file_name(), version=get_version())


@app.route('/upload', methods=["POST"])
def upload():
    if os.path.exists(SERVER_SETUP_LOCATION):
        # cannot re-configure a running storage agent
        flash("Server Already Setup", category='error')
        print("setup flag file already exists")
        abort(409)
    f = request.files.get("file", None)
    if not f:
        flash("No File Uploaded", category='error')
        print("no file found in request")
        abort(400)
    f.save(STORAGE_BUNDLE_LOCATION)
    flash("Uploaded Configuration")

    try:
        setup_storage_agent(STORAGE_BUNDLE_LOCATION)
    except ExceptionReply as e:
        if e.get_type() == PBException.BAD_CREDENTIAL:
            print("Signin token already used")
            abort(403)
        else:
            raise e

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
    r = requests.post("{}/boot/{}".format(LOADER_URL, target))
    r.raise_for_status()


@app.route('/json-get-boot', methods=["GET"])
def get_boot():
    global boot_mode_cached
    if boot_mode_cached is None:
        r = requests.get("{}/boot". format(LOADER_URL))
        r.raise_for_status()
        boot_mode_cached = r.json()
    return jsonify(boot_mode_cached)


@app.route('/json-needs-upgrade', methods=["GET"])
def json_needs_upgrade_get():
    current_version = get_version()
    latest = _latest_version_from_registry()
    print("GET needs upgrade current {} latest {}".format(current_version, latest))
    return jsonify(**{
        'needs-upgrade': LooseVersion(latest) > LooseVersion(current_version)
    })


@app.route('/json-switch-appliance', methods=["POST"])
def json_switch_appliance_post():
    latest = _latest_version_from_registry()
    print("Switching to appliance {}".format(latest))
    r = requests.post("{}/switch/{}/{}/default".format(LOADER_URL, REG, latest))
    r.raise_for_status()


def _pull_images_status():
    r = requests.get("{}/images/pull".format(LOADER_URL))
    r.raise_for_status()
    return r.json()


@app.route('/json-pull-images', methods=["GET", "POST"])
def json_pull_images_post():
    if request.method == "POST":
        pull_stats = _pull_images_status()
        if pull_stats.get("status", "") == "pulling":
            abort(400, description="Upgrade already in progress")
        latest = _latest_version_from_registry()
        print("Pull new images for version {}".format(latest))

        r = requests.post("{}/images/pull/{}/{}".format(LOADER_URL, REG, latest))
        if r.status_code == 409:
            print("A pull is already in progress.")
        elif r.status_code != 200:
            print("Pulling images failed with message: {}".format(r.json()))

        return jsonify(status_code=r.status_code)
    else:
        pull_status = _pull_images_status()
        status = pull_status.get("status", "error")
        if status == "error":
            print("Failed to pull images: {}".format(pull_status.get("message", "")))
        elif status == "done":
            print("all images pulled")
        else:
            stats = "{} of {}".format(pull_status.get("pulled", -1), pull_status.get("total", -1))
            print("Pull images status, status {} ({})".format(status, stats))

        return jsonify({
            'running': status == "pulling",
            'succeeded': status == "done",
            'pulling': pull_status.get("pulled", -1),
            'total': pull_status.get("total", -1)
        })


@app.route('/json-gc', methods=["GET", "POST"])
def json_gc_post():
    if request.method == "POST":
        print("Clean old images POST")
        r = requests.post("{}/gc".format(LOADER_URL))
        r.raise_for_status()
        return jsonify(status_code=r.status_code)
    else:
        r = requests.get("{}/gc".format(LOADER_URL))
        r.raise_for_status()
        status = r.json().get("status", "error")
        return jsonify({
            'running': status == "cleaning",
            'succeeded': status == "done"
        })


@app.route('/json-has-disk-space', methods=["GET"])
def json_disk_space():
    s = os.statvfs('/')
    disk_space = (s.f_bavail * s.f_frsize)
    print("has_disk_space {} {}".format(disk_space, disk_needed))
    return jsonify(has_disk_space=disk_space >= disk_needed)


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


def write_key_to_file(key, path):
    with open(path, 'w') as f:
        f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))


def write_cert_to_file(cert, path):
    with open(path, 'w') as f:
        f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))


if __name__ == "__main__":
    setup_key_cert()
    app.secret_key = "secretkey"
    app.run("0.0.0.0", 443, ssl_context=(SERVER_CERT_LOCATION, SERVER_KEY_LOCATION))

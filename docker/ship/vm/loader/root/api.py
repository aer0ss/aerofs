from flask import Flask, jsonify, request
from uuid import uuid4
import subprocess
from os.path import exists
from constants import MODIFIED_YML_PATH

DOCKER_SOCK_PATH = '/var/run/docker.sock'

BOOT_ID = uuid4().hex

app = Flask(__name__)


def start():
    app.run('0.0.0.0', 80)


@app.route("/boot", methods=["GET"])
def route_get_boot_id():
    """
    Get the current boot id.
    """
    return jsonify(boot_id=BOOT_ID)


@app.route("/boot", methods=["POST"])
def route_reboot():
    """
    Reboot the entire system
    """
    print 'App restart requested.'

    if exists(DOCKER_SOCK_PATH):
        print 'Killing all containers...'
        subprocess.check_call(['crane', 'kill', '-c', MODIFIED_YML_PATH])
        print 'Killing myself. Expecting external system to restart me...'
        shutdown_server()
    else:
        print
        print "WARNING: {} is not available. Please restart the app manually with 'crane kill && crane run'."\
            .format(DOCKER_SOCK_PATH)
        print

    return ''


def shutdown_server():
    """
    See http://flask.pocoo.org/snippets/67/
    """
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        raise RuntimeError('Not running with the Werkzeug Server')
    func()
    # app.run() will exit.
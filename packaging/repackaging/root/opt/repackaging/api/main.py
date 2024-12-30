import os
import subprocess
import sys
import psutil
from flask import Flask, jsonify, send_from_directory
from aerofs_common.configuration import Configuration

REPACKAGER = '/opt/repackaging/tools/repackage-installers.sh'
DIST = '/opt/repackaging/installers/modified'
DONE_FILE = DIST + '/.repackage-done'

app = Flask(__name__)

@app.errorhandler(Exception)
def all_exception_handler(error):
   print(error)
   return 'Error', 500

@app.route("/", methods=["GET"])
def get():
    """
    Return the status of the repackaging task.
    Note: backup_view.py shares very similar logic.
    """
    running = is_running()
    return jsonify(
        running=running,
        succeeded=not running and os.path.isfile(DONE_FILE)
    )


@app.route("/", methods=["POST"])
def post():
    """
    Launch the repackaging task.
    Note: backup_view.py shares very similar logic.
    """
    if is_running():
        return jsonify(error='already running'), 400

    # N.B. potential race condition between invoking is_running() and Popen(). Fortunately Flask
    # serializes requests.

    config = Configuration('http://config.service:5434', 'repackaging')
    config_service_public_url = config.server_properties()['config.loader.configuration_service_url']
    print(('Launch repackaging task (config service url:', config_service_public_url, ')'))

    subprocess.Popen([REPACKAGER, config_service_public_url, '/opt/repackaging/cacert.pem'])

    return '', 204


@app.route('/installers/<path:filename>', methods=["GET"])
def send_foo(filename):
    return send_from_directory(DIST, filename)


def is_running():
    for pid in psutil.pids():
        cmdline = psutil.Process(pid).cmdline()
        # The expected cmdline is [ '/bin/bash', REPACKAGER, ... ]
        if len(cmdline) > 1 and cmdline[1] == REPACKAGER:
            return True
    return False


def banner():
    return '''
API Usage:

    GET /       Return the status of the repackaging task. Response:
                {
                    "running": true,        // Whether the repackaging task is running
                    "succeeded": false      // Whether the last run succeeded. Undefined if the task
                                            // is running.
                }

    POST /      Launch the repackaging task. It would fail if the task is already running.

    GET /installers/<file>  Download a given installer. Available only after the last repackaging
                            succeeds.
    '''


if __name__ == "__main__":
    print((banner()))
    app.run('0.0.0.0', 80)

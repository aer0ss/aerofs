from flask import Flask, json, jsonify, request
from uuid import uuid4
from common import call_crane, my_container_name, my_image_name, MODIFIED_YML_PATH
import yaml

PREFIX = '/v1'
CURRENT = 'current'

BOOT_ID = uuid4().hex

app = Flask(__name__)

_current_repo = None
_current_target = None
_repo_file = None
_target_file = None
_tag = None
_simulate = None


def start(current_repo, current_target, repo_file, target_file, tag, simulate):
    global _current_repo, _current_target, _repo_file, _target_file, _tag, _simulate
    # Save data in RAM rather than reading files on demand as their content may change _after_ we launch.
    _current_repo = current_repo
    _current_target = current_target
    _repo_file = repo_file
    _target_file = target_file
    _tag = tag
    _simulate = simulate

    print "Starting API service..."
    app.run('0.0.0.0', 80)


@app.route(PREFIX + "/boot", methods=["GET"])
def get_boot():
    """
    Get the current boot id.
    """
    return jsonify(
        id=BOOT_ID,
        registry=_current_repo,
        tag=_tag,
        target=_current_target
    )

@app.route(PREFIX + "/boot", methods=["POST"])
@app.route(PREFIX + "/boot/<repo>", methods=["POST"])
@app.route(PREFIX + "/boot/<repo>/<tag>", methods=["POST"])
@app.route(PREFIX + "/boot/<repo>/<tag>/<target>", methods=["POST"])
def post_boot(repo=CURRENT, tag=CURRENT, target=CURRENT):
    """
    Reboot the entire system
    """
    print 'Restarting app to {}/{}/{} (repo/tag/target)...'.format(repo, tag, target)

    if repo != CURRENT:
        return 'Supporting non-{} repos is not implemented yet'.format(CURRENT), 400

    if _simulate:
        print
        print "WARNING: simulation mode. I dare not restart the appliance. Please do so manually."
        print
    else:
        call_crane('kill', _current_target)
        print 'Killing myself. Expecting external system to restart me...'
        shutdown_server()

    # For safety, update files _after_ everything shuts down.
    if repo != CURRENT:
        with open(_repo_file, 'w') as f:
            f.write(repo)
    if target != CURRENT:
        with open(_target_file, 'w') as f:
            f.write(target)

    return ''


def shutdown_server():
    """
    See http://flask.pocoo.org/snippets/67/
    """
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        raise Exception('Not running with the Werkzeug Server')
    func()
    # app.run() will exit.


@app.route(PREFIX + "/containers")
def get_containers():
    with open(MODIFIED_YML_PATH) as f:
        y = yaml.load(f)

    # Manually insert the Loader container as it's been removed from the modified yaml.
    ret = {my_container_name(): my_image_name()}

    for key, c in y['containers'].iteritems():
        ret[key] = c['image']

    return json.dumps(ret)
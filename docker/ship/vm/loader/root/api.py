from flask import Flask, json, jsonify, request
from uuid import uuid4
from common import call_crane, my_container_name, my_image_name, MODIFIED_YML_PATH
import yaml
import requests

PREFIX = '/v1'
CURRENT = 'current'

BOOT_ID = uuid4().hex

app = Flask(__name__)

_current_repo = None
_current_target = None
_repo_file = None
_tag_file = None
_target_file = None
_tag = None


def start(current_repo, current_target, repo_file, tag_file, target_file, tag):
    global _current_repo, _current_target, _repo_file, _tag_file, _target_file, _tag
    # Save data in RAM rather than reading files on demand as their content may change _after_ we launch.
    _current_repo = current_repo
    _current_target = current_target
    _repo_file = repo_file
    _tag_file = tag_file
    _target_file = target_file
    _tag = tag

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
@app.route(PREFIX + "/boot/<target>", methods=["POST"])
def post_boot(target=CURRENT):
    """
    Reboot the entire system
    """
    print 'Restarting app to /{}...'.format(target)

    call_crane('kill', _current_target)
    print 'Killing myself. Expecting external system to restart me...'
    shutdown_server()

    # For safety, update files _after_ everything shuts down.
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


@app.route(PREFIX + "/containers", methods=["GET"])
def get_containers():
    with open(MODIFIED_YML_PATH) as f:
        y = yaml.load(f)

    # Manually insert the Loader container as it's been removed from the modified yaml.
    ret = {my_container_name(): my_image_name()}

    for key, c in y['containers'].iteritems():
        ret[key] = c['image']

    return json.dumps(ret)


@app.route(PREFIX + "/tags/latest", methods=["GET"])
@app.route(PREFIX + "/tags/latest/<repo>", methods=["GET"])
def get_latest_tag(repo=None):
    if not repo:
        repo = _current_repo
    url = 'https://{}/v1/repositories/{}/tags'.format(repo, my_image_name())
    print "Querying latest tag at {}...".format(url)
    r = requests.get(url)
    if 200 <= r.status_code < 300:
        print "Server {} returned: {}".format(repo, r.text)
        ret = r.json()
        for k, v in ret.iteritems():
            if k != 'latest' and v == ret['latest']:
                return '"{}"'.format(k)
        return '"The latest tag does not correspond to a version."', 502
    else:
        return r.text, r.status_code

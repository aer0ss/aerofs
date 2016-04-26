import datetime
import os
import requests
import subprocess
import yaml
from common import call_crane, my_container_id, my_container_name, my_full_image_name, my_image_name, print_args, \
    MODIFIED_YML_PATH, my_container_prefix, my_subdomain, get_port_number_from_port_allocator
from flask import Flask, json, jsonify, request, make_response
from os.path import exists
from os import devnull
from traceback import print_exc
from uuid import uuid4

PREFIX = '/v1'
CURRENT = 'current'

BOOT_ID = uuid4().hex

OS_CMD = ["docker", "run", "--rm", "-v", "/:/host", "alpine:3.3", "/bin/sh", "-c"]
OS_UPDATE_CMD = OS_CMD + ["chroot /host /bin/update_engine_client -update"]
OS_REBOOT_CMD = OS_CMD + ["chroot /host reboot"]
OS_TYPE = OS_CMD + ["chroot /host uname -a"]
UPDATE_FINISHED="UPDATE_STATUS_UPDATED_NEED_REBOOT"

OS_ALREADY_UPDATED = "update failed"
OS_UPDATE_OUT = '/os_update.out'
OS_UPDATE_ERR = '/os_update.err'
TIMEOUT = 600

app = Flask(__name__)

_current_repo = None
_current_target = None
_repo_file = None
_tag_file = None
_target_file = None
_tag = None

_update_proc = None
_start = None

# This file exists because:
# 1. We want to have a configurable registry endpoint for the appliance such that when the appliance
# upgrades next, it uses this configurable registry endpoint.
# 2. This endpoint is configurable from bunker(and getty). If bunker modifies the registry endpoint,
# we have to store this somewhere in the loader.
# 3. If we were to overwrite the repo file simply, when the loader restarts next(for example if appliance
# maintenance mode is toggled), then we will incorrectly modify the "modified-yml" file with the new registry
# endpoint eventhough we haven't upgraded yet.
# 4. So, use this file to track the new registry endpoint so that we only change the actual repo file only
# when we switch to a version downloaded from this new registry endpoint.
# 5. The switch function empties this file.
_custom_repo_file = "/custom.repo"


def start(current_repo, current_target, repo_file, tag_file, target_file, tag):
    global _current_repo, _current_target, _repo_file, _tag_file, _target_file, _tag, _custom_repo_file
    # Save data in RAM rather than reading files on demand as their content may change _after_ we launch.

    # Make sure _current_repo points to the new registry endpoint if it exists
    # otherwise make it point to the registry in the repo file. This is because
    #
    with open(_custom_repo_file) as f:
        custom_repo = f.read().strip()
    if custom_repo:
        _current_repo = custom_repo
    else:
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
    print 'Reboot to /{}...'.format(target)
    kill_other_containers()
    return restart_to(target)


@app.route(PREFIX + "/repo/<repo>", methods=["POST"])
def post_repo(repo):
    global _custom_repo_file, _current_repo

    if _custom_repo_file and os.path.isfile(_custom_repo_file):
        with open(_custom_repo_file, 'w') as f:
            f.write(repo)
        _current_repo = repo
    else:
        return make_response("Repo file not found", 500)
    return make_response("Registry changed to {}".format(repo), 200)


@app.route(PREFIX + "/repo", methods=["GET"])
def get_repo():
    global _current_repo
    return jsonify(repo=_current_repo)

def kill_other_containers():
    call_crane('kill', _current_target)


def restart_to(target):
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
    ret = {my_container_name(): my_full_image_name()}

    for key, c in y['containers'].iteritems():
        ret[key] = c['image']

    return json.dumps(ret)


def _get_img_id(url, tag):
    # Get image id. Unfortunately as of this writing no easy way to get it for v2.
    r  = requests.get("{}/manifests/{}".format(url, tag))
    r.raise_for_status()
    return json.loads(str(r.json()['history'][0]['v1Compatibility'])).get('id', None)


def _get_latest_tag_from_v2_registry(repo):
    url = "https://{}/v2/{}".format(repo, my_image_name())
    print "Querying latest tag at {}...".format(url)
    r = requests.get("{}/tags/list".format(url))
    r.raise_for_status()

    # Remove latest from the list
    tags = r.json()['tags']
    tags.remove('latest')
    # Sort in descending order so that we can return ASAP
    tags.sort(reverse=True)

    latest_img_id = _get_img_id(url, "latest")
    if not latest_img_id:
        return "Unable to get the latest image id from registry", 500

    for tag in tags:
        img_id = _get_img_id(url, tag)
        if img_id == latest_img_id:
            return tag, 200

    return "Unable to get the latest tag from registry", 500


def _get_latest_tag_from_v1_registry(repo):
    url = 'https://{}/v1/repositories/{}/tags'.format(repo, my_image_name())
    print "Querying latest tag at {}...".format(url)
    r = requests.get(url)
    if 200 <= r.status_code < 300:
        print "Server {} returned: {}".format(repo, r.text)
        ret = r.json()
        for k, v in ret.iteritems():
            if k != 'latest' and v == ret['latest']:
                return k, 200
        return "The latest tag does not correspond to a version.", 500
    else:
        return r.text, r.status_code


def _is_v2_registry(repo):
    url = "https://{}/v2/".format(repo)
    return requests.get(url).status_code == 200


def _is_v1_registry(repo):
    url = "https://{}/v1/_ping".format(repo)
    return requests.get(url).status_code == 200


def _get_latest_tag(repo):
    if not repo:
        if not _current_repo:
            return "Unable to find repo", 500
        repo = _current_repo

    if _is_v2_registry(repo):
        return _get_latest_tag_from_v2_registry(repo)

    elif _is_v1_registry(repo):
        return _get_latest_tag_from_v1_registry(repo)
    else:
        return "Invalid registry", 500


@app.route(PREFIX + "/tags/latest", methods=["GET"])
@app.route(PREFIX + "/tags/latest/<repo>", methods=["GET"])
def get_latest_tag(repo=None):
    text, status = _get_latest_tag(repo)
    return make_response(text, status)


PULL_JSON = '/pull.json'

@app.route(PREFIX + "/images/pull", methods=["POST"])
@app.route(PREFIX + "/images/pull/<repo>/<tag>", methods=["POST"])
def post_images_pull(repo=None, tag=None):
    if not repo:
        repo = _current_repo

    if not tag:
        tag, _ = _get_latest_tag(repo)

    # If tag and repo are still empty or non return 500
    if not repo or not tag:
        fail = "Repo is None or empty" if not repo else "Tag is None or empty"
        return make_response(fail, 500)

    if not exists(PULL_JSON):
        pulling = False
    else:
        with open(PULL_JSON) as f:
            pull_status = json.load(f)
            pulling = pull_status.get("bootid", None) == BOOT_ID and \
                pull_status.get("status", "error") == 'pulling'

    if pulling:
        return make_response("Pulling is already in progress.", 409)
    else:
        print "Pulling from {} tag {} ... The following output is from pull.sh:".format(repo, tag)
        # N.B. there is a small chance of race condition that this path is called again before
        # pull.sh updates PULL_JSON. Maintenance cost of the full solution due to its complexity
        # overweighs the benefit.
        # Pass in BOOT_ID so that if container is killed while pulling new images, new restarted
        # container can compare BOOT_ID.
        subprocess.Popen(['/pull.sh', repo, my_image_name(), tag, PULL_JSON, BOOT_ID])
        return make_response("Pulling started successfully.", 200)


@app.route(PREFIX + "/images/pull", methods=["GET"])
def get_images_pull():
    if exists(PULL_JSON):
        with open(PULL_JSON) as f:
            pull_status = json.load(f)
            # If pull.json exists but bootid is different than current bootid
            # this would mean while pull was in progress loader was restarted for some reason.
            # In this case whatever is in pull.json is useless and just return status=done
            # because that would mean a new pull can be started.
            if pull_status.get("bootid", None) == BOOT_ID:
                return jsonify(**pull_status)

    return jsonify(status='done')


@app.route(PREFIX + "/switch/<target>", methods=["POST"])
def switch(target):
    repo = request.args.get("repo")
    tag =  request.args.get("tag")

    if not exists(PULL_JSON):
        fail = "Please pull new images before trying to switch"
        print fail
        return make_response(fail, 500)
    else:
        with open(PULL_JSON) as f:
            pull_status = json.load(f)
            pulled = pull_status.get("status", "error") == 'done'
            tag = pull_status.get("tag", None) if pulled else None

    if not tag:
        fail = "A pull is already in progress or failed. Wait for pull images to finish or retry pulling images."
        print fail
        return make_response(fail, 500)

    if not repo:
        if not _current_repo:
            fail = "Repo not found. Cannot switch."
            print "{}. Are you trying to switch appliances/IPU your local dk appliance? This is not possible".format(fail)
            return make_response(fail, 500)
        else:
            repo = _current_repo


    kill_other_containers()
    print "Switching to version: {}".format(tag)
    print 'Computing data volumes to be copied from the old containers...'

    # List old containers
    # old: map of original-name => (old modified-name, image)
    old = {}
    with open(MODIFIED_YML_PATH) as f:
        for k, v in yaml.load(f)['containers'].iteritems():
            old[v['original-name']] = (k, v['image'])

    # List the new containers that haven't been created and have shared volumes with their old counterparts.
    # new: map of new modified-name => (old modified-name, volumes shared by both old and new containers)
    new = {}
    new_loader_image = '{}/{}:{}'.format(repo, my_image_name(), tag)
    cmd = ['docker', 'run', '--rm', '-v', '/var/run/docker.sock:/var/run/docker.sock', new_loader_image, 'modified-yml',
           repo, tag]

    # TODO: Refactor this. Currently, we rely on this call outputting valid yaml and
    # nothing else. Better would be to output to a file and read from that.
    for new_container, new_container_props in yaml.load(subprocess.check_output(print_args(cmd)))['containers'].iteritems():
        counterpart = old.get(new_container_props['original-name'])
        if not counterpart:
            print '{} has no counterpart'.format(new_container)
            continue
        volumes = volume_set_of(new_container_props['image'])
        if not volumes:
            print '{} has no data volumes'.format(new_container)
            continue
        counterpart_volumes = volume_set_of(counterpart[1])
        if not counterpart_volumes:
            print '{}\'s counterpart has no data volumes'.format(new_container)
            continue
        intersected_volumes = volumes & counterpart_volumes
        if not intersected_volumes:
            print '{} has no intersecting data volumes'.format(new_container)
            continue
        if has_container(new_container):
            print '{} already exists'.format(new_container)
            continue
        if not has_container(counterpart[0]):
            print '{} doesn\'t exist'.format(counterpart[0])
            continue
        new[new_container] = (counterpart[0], intersected_volumes)

    print 'Containers for data copying, in the format of "target-container: (source-container, volumes)":'
    print new

    # Create the loader container first. Otherwise when launching the containers that we create below, the system would
    # fail if one of them links to the new loader -- the `create-containers` command doesn't create the loader.
    new_loader = create_loader_container(new_loader_image, tag)

    if new:
        # Create containers
        ####
        # FIXME: This step runs a loader without a specific name. As a result, this loader will have an auto-
        # generated name which means that it will incorrectly assume that it's running Private Cloud even in HPC.
        # What this means is that in-place upgrades most likely won't work in HPC.
        # Also, we really should be using docker-py instead of shelling out to docker-py.
        #####
        cmd = ['docker', 'run', '--rm', '-v', '/var/run/docker.sock:/var/run/docker.sock', new_loader_image,
               'create-containers', repo, tag, new_loader]
        cmd.extend(new.keys())
        subprocess.check_call(print_args(cmd))

        # Copy data
        loader_image = my_full_image_name()
        for container, new_container_props in new.iteritems():
            cmd = ['/copy-container-data.sh', loader_image, new_container_props[0], container]
            cmd.extend(list(new_container_props[1]))
            subprocess.check_call(print_args(cmd))

    with open(_repo_file, 'w') as f:
            f.write(repo)

    # Make sure custom_repo_file is empty again now that we are switching.
    if _custom_repo_file and os.path.isfile(_custom_repo_file):
        with open(_custom_repo_file, 'w') as f:
            f.write("")

    with open(_tag_file, 'w') as f:
            f.write(tag)

    return restart_to(target)


GC_JSON = '/gc.json'

@app.route(PREFIX + "/gc", methods=["POST"])
def post_gc():
    if not exists(GC_JSON):
        cleaning = False
    else:
        with open(GC_JSON) as f:
            clean_status = json.load(f)
            cleaning = clean_status.get("bootid", None) == BOOT_ID and \
                clean_status.get("status", "error") == 'cleaning'

    if cleaning:
        return '"Cleaning is already in progress."', 409

    # TODO currently GC don't perform cleaning for loader images of the same tag but different repo names than the
    # current loader.
    subprocess.Popen(print_args(['/gc.sh', my_image_name(), _tag, GC_JSON, BOOT_ID]))
    return '"gc started"', 200


@app.route(PREFIX + "/gc", methods=["GET"])
def get_gc():
    if exists(GC_JSON):
        with open(GC_JSON) as f:
            clean_status = json.load(f)
            # See get_images_pull for why we do this.
            if clean_status.get("bootid", None) == BOOT_ID:
                return jsonify(**clean_status)

    return jsonify(status='done')


@app.route(PREFIX + "/port/<port_name>/<default_value>", methods=["GET"])
def get_port(port_name, default_value):
    """
    On HPC, relays a request for port numbers to the port allocator service.
    On PC, simply returns the default port.
    This route is called by the config service upon start.
    """
    subdomain = my_subdomain()
    if subdomain:  # HPC
        return get_port_number_from_port_allocator(subdomain, port_name)
    else:  # PC
        return str(default_value)


def has_container(container):
    with open(devnull, 'w') as null:
        return subprocess.call(print_args(['docker', 'inspect', container]), stdout=null, stderr=null) == 0


def volume_set_of(image):
    """
    :return: the set of data volumes defined for the given Docker image
    """
    cmd = ['docker', 'inspect', image]
    vols = yaml.load(subprocess.check_output(print_args(cmd)))[0]['Config']['Volumes']
    return set(vols.keys()) if vols else None


def create_loader_container(loader_image, tag):
    # N.B. this name must be consistent with the definition in cloud-config.yml.jinja
    # We use a prefix in hosted private cloud so that multiple instances of the containers can run on the same box
    container = "{}loader-{}".format(my_container_prefix(), tag)

    if not has_container(container):
        # Retrieve runtime parameters from the current loader and reuse them for the new loader.
        inspect = yaml.load(subprocess.check_output(['docker', 'inspect', my_container_id()]))

        cmd = ['docker', 'create', '--name', container]
        for bind in inspect[0]['HostConfig']['Binds']:
            cmd.append('-v')
            cmd.append(bind)
        cmd.append(loader_image)
        cmd.extend(inspect[0]['Config']['Cmd'])
        subprocess.check_call(print_args(cmd))

    return container


@app.errorhandler(500)
def internal_error(error):
    print_exc(error)
    return '"An internal server error has occurred. Check logs for more info."', 500


@app.route(PREFIX + "/reboot-vm", methods=["POST"])
def post_reboot_vm():
    subprocess.Popen(print_args(OS_REBOOT_CMD))
    return ''

@app.route(PREFIX + "/vm-os", methods=["GET"])
def get_vm_os():
    return subprocess.check_output(print_args(OS_TYPE))


def _mark_os_update_inactive():
    os.remove(OS_UPDATE_OUT)
    os.remove(OS_UPDATE_ERR)
    _start = None
    _update_proc = None


@app.route(PREFIX + "/update-os", methods=["GET"])
def get_os_update():
    if _update_proc is None:
        return jsonify(status='done', succeeded=False)
    if _update_proc.poll() is None:
        # Allow update process to run for 600 secs before killing.
        if (datetime.datetime.now() - _start).total_seconds() > TIMEOUT:
            os.kill(process.pid, signal.SIGKILL)
            os.waitpid(-1, os.WNOHANG)
            _mark_os_update_inactive()
            return jsonify(status='done', succeeded=False)

        return jsonify(status='running', succeeded=False)

    on_latest = False
    with open(OS_UPDATE_ERR, 'r') as f:
        for line in f:
            if OS_ALREADY_UPDATED in line.lower():
                on_latest = True
                break

    if on_latest:
        _mark_os_update_inactive()
        return jsonify(status='error', succeeded=True)

    with open(OS_UPDATE_OUT) as f:
        content = dict([line.strip().split('=') for line in f])

    _mark_os_update_inactive()
    return jsonify(status='done', succeeded=(content.get('CURRENT_OP', None) == UPDATE_FINISHED))


@app.route(PREFIX + "/update-os", methods=["POST"])
def post_os_update():
    if _update_proc is not None and _start is not None:
        return '"Update is already in progress."', 409

    assert not os.path.exists(OS_UPDATE_OUT) and not os.path.exists(OS_UPDATE_ERR)

    _start = datetime.datetime.now()
    with open(OS_UPDATE_OUT, 'w') as fout, open(OS_UPDATE_ERR, 'w') as ferr:
        _update_proc = subprocess.Popen(print_args(OS_UPDATE_CMD), stdout=fout, stderr=ferr)
    return '"os update started"', 200


from flask import Flask, json, jsonify, request, make_response
from uuid import uuid4
from os.path import exists
from os import devnull
from common import call_crane, my_container_id, my_container_name, my_full_image_name, my_image_name, print_args, \
    MODIFIED_YML_PATH, my_container_prefix, my_subdomain
import yaml, requests, subprocess
from traceback import print_exc

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
    print 'Reboot to /{}...'.format(target)
    kill_other_containers()
    return restart_to(target)


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


@app.route(PREFIX + "/tags/latest/<repo>", methods=["GET"])
def get_latest_tag(repo):
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


PULL_JSON = '/pull.json'

@app.route(PREFIX + "/images/pull/<repo>/<tag>", methods=["POST"])
def post_images_pull(repo, tag):
    if not exists(PULL_JSON):
        pulling = False
    else:
        with open(PULL_JSON) as f:
            pull_status = json.load(f)
            pulling = pull_status.get("bootid", None) == BOOT_ID and \
                pull_status.get("status", "error") == 'pulling'

    if pulling:
        return '"Pulling is already in progress."', 409
    else:
        print "Pulling from {} tag {} ... The following output is from pull.sh:".format(repo, tag)
        # N.B. there is a small chance of race condition that this path is called again before
        # pull.sh updates PULL_JSON. Maintenance cost of the full solution due to its complexity
        # overweighs the benefit.
        # Pass in BOOT_ID so that if container is killed while pulling new images, new restarted
        # container can compare BOOT_ID.
        subprocess.Popen(['/pull.sh', repo, my_image_name(), tag, PULL_JSON, BOOT_ID])
        return '"Pulling started successfully."'


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


@app.route(PREFIX + "/switch/<repo>/<tag>/<target>", methods=["POST"])
def switch(repo, tag, target):
    kill_other_containers()

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
        r = requests.get('http://hpc-port-allocator.service/ports/{}/{}'.format(subdomain, port_name))
        return make_response((r.text, r.status_code, None))
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

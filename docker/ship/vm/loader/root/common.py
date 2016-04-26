import subprocess
from docker import Client
from socket import gethostname
from re import sub, match

MODIFIED_YML_PATH = '/modified.yml'
TAG_PATH = '/tag'


def call_crane(cmd, target):
    args = ['crane', cmd]

    # empty target to run the default group or all containers if no default group is defined.
    if target and target != 'default':
        args.append(target)

    # '-d all' is for the case where not all dependencies are specified in the target group.
    args.extend(['-d', 'all', '-c', MODIFIED_YML_PATH])

    subprocess.check_call(print_args(args))


def my_container_id():
    """
    :return ID of the current container. It assumes the host didn't alter the hostname when launching the container.
    """
    return gethostname()


def my_full_image_name():
    """
    :return: The loader's image name
    """
    return subprocess.check_output(['docker', 'inspect', '-f', '{{ .Config.Image }}', my_container_id()]).strip()


def my_image_name():
    """
    :return: The loader's image name, with repo and tag removed, if any.
    """
    # Remove tag
    image = sub(':[^:/]+$', '', my_full_image_name())
    # Remove repo
    if image.count('/') > 1:
        image = sub('^[^/]*/', '', image)
    return image


_container_name = None


def my_container_name():
    """
    :returns the container name. Cache the result on first use so that subsequent calls don't shell out to docker again.
    """
    global _container_name
    if not _container_name:
        _container_name = subprocess.check_output(['docker', 'inspect',
            '-f', '{{ .Name }}',
            my_container_id()]).strip('/ \n\t\r')

    return _container_name


def my_container_prefix():
    # Note we use the following format for the prefix: <subdomain>-hpc-loader
    # The inclusion of the -hpc- part is to prevent the loader from accidentally thinking it's running on HPC if someone
    # uses the loader for another project and names it, e.g. "myproject-loader"
    #
    # Note: this must be kept in sync with lizard (~/repos/aerofs/src/lizard/lizard/hpc.py)
    m = match("^(.*-hpc-)loader", my_container_name())
    return m.groups()[0] if m else ''


def my_subdomain():
    """
    :returns the subdomain if we're running on HPC or None in Private Cloud.
    The subdomain is defined as a prefix on the loader container name.
    Note that this will fail to return the correct result on HPC if we're running as an anonymous container.
    """
    prefix = my_container_prefix()
    # remove the trailing '-hpc-' from the prefix
    return prefix[:-5] if len(prefix) > 5 else None


def get_tag():
    with open(TAG_PATH) as f:
        return f.read().strip()


def get_port_number_from_port_allocator(subdomain, port_name):
    # Get the docker client
    client = Client(base_url='unix://var/run/docker.sock', version='auto')
    port_allocator_IP_address = client.inspect_container('hpc-port-allocator')['NetworkSettings']['IPAddress']
    r = requests.get('http://{}/ports/{}/{}'.format(port_allocator_IP_address, subdomain, port_name))
    r.raise_for_status()
    return int(r.text)


def print_args(args):
    print '>>>', args
    return args

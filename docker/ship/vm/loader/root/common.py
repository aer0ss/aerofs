import subprocess
from socket import gethostname
from re import sub

MODIFIED_YML_PATH = '/modified.yml'
CRANE_YML_PATH = '/crane.yml'
TAG_PATH = '/tag'


def call_crane(cmd, target):
    args = ['crane', cmd]

    # empty target to run the default group or all containers if no default group is defined.
    if target and target != 'default':
        args.append(target)

    # '-d all' is for the case where not all dependencies are specified in the target group.
    args.extend(['-d', 'all', '-c', MODIFIED_YML_PATH])

    subprocess.check_call(print_args(args))


def add_tag_to_container(c, tag):
    """
    See also: patterns defined in modify_links()
    """
    return '{}-{}'.format(c, tag)


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


def my_container_name():
    return subprocess.check_output(['docker', 'inspect', '-f', '{{ .Name }}', my_container_id()]).strip()


def get_tag():
    with open(TAG_PATH) as f:
        return f.read().strip()

def print_args(args):
    print '>>>', args
    return args

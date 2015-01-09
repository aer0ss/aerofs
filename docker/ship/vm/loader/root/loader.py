from socket import gethostname
from re import compile
import subprocess
import yaml
from constants import MODIFIED_YML_PATH, CRANE_YML_PATH, TAG_PATH
from api import start


def load(alternative_tag=None):
    modify_yaml(alternative_tag)

    print 'Starting all containers...'
    subprocess.check_call(['crane', 'run', '-c', MODIFIED_YML_PATH])

    start()


def modify_yaml(alternative_tag):
    """
    :param alternative_tag When not None, override the value in TAG_PATH.
    """
    tag = alternative_tag if alternative_tag else get_tag()

    with open(CRANE_YML_PATH) as f:
        y = yaml.load(f)

    containers = y['containers']
    my_image = my_image_name()
    my_container = my_container_name()

    loader_container = remove_loader_container(containers, my_image)
    tag_images(containers, tag)
    modify_links(containers, loader_container, my_container, tag)
    modify_volumes_from(containers, loader_container, my_container, tag)

    with open(MODIFIED_YML_PATH, 'w') as f:
        f.write(yaml.dump(y, default_flow_style=False))


def remove_loader_container(containers, my_image):
    loader_container = None
    for key, c in containers.iteritems():
        if c['image'] == my_image:
            if loader_container:
                raise Exception('{} has multiple containers that refer to {}'.format(CRANE_YML_PATH, my_image))
            loader_container = key

    if not loader_container:
        raise Exception('{} contains no Loader image {}'.format(CRANE_YML_PATH, my_image))
    del containers[loader_container]

    return loader_container


def tag_images(containers, tag):
    for key in containers.keys():
        c = containers.pop(key)
        c['image'] = '{}:{}'.format(c['image'], tag)
        # Add the container back with the new container name
        containers['{}-{}'.format(key, tag)] = c


def modify_links(containers, loader_container, my_container, tag):
    """
    This method adds tag to all container links, and replace links to the Loader container with my container name
    """
    tag_pattern = compile(':')
    tag_replace = '-{}:'.format(tag)

    loader_pattern = compile('^{}-{}:'.format(loader_container, tag))
    loader_replace = '{}:'.format(my_container)

    for c in containers.itervalues():
        if 'run' in c and 'link' in c['run']:
            links = [tag_pattern.sub(tag_replace, link) for link in c['run']['link']]
            c['run']['link'] = [loader_pattern.sub(loader_replace, link) for link in links]


def modify_volumes_from(containers, loader_container, my_container, tag):
    """
    This method adds tag to all volumes-from links, and replace links to the Loader container with my container name
    """
    # Replace links to Loader container with my container name
    loader_pattern = compile('^{}-{}$'.format(loader_container, tag))
    loader_replace = my_container

    for c in containers.itervalues():
        if 'run' in c and 'volumes-from' in c['run']:
            volumes = ['{}-{}'.format(link, tag) for link in c['run']['volumes-from']]
            c['run']['volumes-from'] = [loader_pattern.sub(loader_replace, link) for link in volumes]


def my_container_id():
    """
    :return ID of the current container. It assumes the host didn't alter the hostname when launching the container.
    """
    return gethostname()


def my_image_name():
    """
    :return: The loader's image name. We assume /opt/ship/sail on the host always starts us using the original image
    name defined in crane.yml, with no tags or repo names. Thus, we can find the loader definition in crane.yml by
    searching this file for the returned image name.
    """
    return subprocess.check_output(['docker', 'inspect', '-f', '{{ .Config.Image }}', my_container_id()]).strip()


def my_container_name():
    return subprocess.check_output(['docker', 'inspect', '-f', '{{ .Name }}', my_container_id()]).strip()


def get_images():
    ret = set()
    with open(CRANE_YML_PATH) as f:
        containers = yaml.load(f)['containers']
    for key in containers:
        ret.add(containers[key]['image'])
    # Sort to help us monitor the progress of tasks that enumerate images.
    return sorted(ret)


def get_tag():
    with open(TAG_PATH) as f:
        return f.read().strip()


def verify():
    if not get_tag():
        raise Exception('{} is empty.'.format(TAG_PATH))

    elif get_tag() == 'latest':
        raise Exception('Using "latest" as the tag is not allowed, as it couldn\'t guarantee' +
                        ' version consistency across containers.')

    for image in get_images():
        if ':' in image:
            raise Exception('Image name "{}" has a colon.'.format(image))
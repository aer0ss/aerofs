from re import compile
import yaml
from common import MODIFIED_YML_PATH, CRANE_YML_PATH, my_image_name, my_container_name


def modify_yaml(repo, tag):

    with open(CRANE_YML_PATH) as f:
        y = yaml.load(f)

    containers = y['containers']
    my_image = my_image_name()
    my_container = my_container_name()

    loader_container = remove_loader_container(containers, my_image)
    tagged_loader_container = add_tag_to_container(loader_container, tag)
    add_repo_and_tag_to_images(containers, repo, tag)
    modify_links(containers, tagged_loader_container, my_container, tag)
    modify_volumes_from(containers, tagged_loader_container, my_container, tag)

    if 'groups' in y:
        modify_groups(y['groups'], tagged_loader_container, my_container, tag)

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


def add_repo_and_tag_to_images(containers, repo, tag):
    prefix = image_prefix(repo)
    suffix = image_suffix(tag)
    for key in containers.keys():
        c = containers.pop(key)
        c['image'] = '{}{}{}'.format(prefix, c['image'], suffix)
        # Add the container back with the new container name
        containers[add_tag_to_container(key, tag)] = c


def image_prefix(repo):
    return '{}/'.format(repo) if repo else ''


def image_suffix(tag):
    return ':{}'.format(tag) if tag else ''


def container_suffix(tag):
    return '-{}'.format(tag) if tag else ''


def modify_links(containers, tagged_loader_container, my_container, tag):
    """
    Add tag to all container links and replace links to the Loader container with my container name
    """
    tag_pattern = compile(':')
    tag_replace = '{}:'.format(container_suffix(tag))
    loader_pattern = compile('^{}:'.format(tagged_loader_container))

    for c in containers.itervalues():
        if 'run' in c and 'link' in c['run']:
            links = [tag_pattern.sub(tag_replace, link) for link in c['run']['link']]
            c['run']['link'] = [loader_pattern.sub('{}:'.format(my_container), link) for link in links]


def modify_volumes_from(containers, tagged_loader_container, my_container, tag):
    """
    Add tag to all volumes-from links and replace links to the Loader container with my container name
    """
    # Replace links to Loader container with my container name
    loader_pattern = compile('^{}$'.format(tagged_loader_container))

    for c in containers.itervalues():
        if 'run' in c and 'volumes-from' in c['run']:
            volumes = [add_tag_to_container(link, tag) for link in c['run']['volumes-from']]
            c['run']['volumes-from'] = [loader_pattern.sub(my_container, link) for link in volumes]


def modify_groups(groups, tagged_loader_container, my_container, tag):
    """
    Add tag to all containers in groups and replace the Loader container with my container name
    """
    # Replace links to Loader container with my container name
    loader_pattern = compile('^{}$'.format(tagged_loader_container))

    for k in groups:
        groups[k] = [add_tag_to_container(c, tag) for c in groups[k]]
        groups[k] = [loader_pattern.sub(my_container, c) for c in groups[k]]


def add_tag_to_container(c, tag):
    """
    See also: patterns defined in modify_links()
    """
    return '{}{}'.format(c, container_suffix(tag))



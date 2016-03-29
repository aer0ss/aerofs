from re import compile
import yaml
import jinja2
import requests
import os.path
import psutil
from common import MODIFIED_YML_PATH, modify_image, my_image_name, my_container_name, my_subdomain, my_container_prefix

CRANE_JINJA_PATH = '/crane.yml.jinja'
CRANE_YML_PATH = '/crane.yml'


def load_crane_yml():
    """
    Returns the content of crane.yml as a dictionnary
    We look first for 'crane.yml.jinja', and if it doesn't exist, we look for crane.yml.
    """
    if os.path.exists(CRANE_JINJA_PATH):
        crane_yml = render_crane_yml(CRANE_JINJA_PATH, my_subdomain())
        return yaml.load(crane_yml)
    else:
        with open(CRANE_YML_PATH) as f:
            return yaml.load(f)


def render_crane_yml(template, subdomain=None):
    """
    :param template: path to the crane.yml.jinja file
    :param subdomain: the subdomain for HPC, or `None` for Private Cloud
    :return: the content of crane.yml after template evaluation
    Note: this function is also called by ~/repos/aerofs/docker/dev/gen-crane-yml.py
    """
    env = jinja2.Environment(loader=jinja2.FileSystemLoader(os.path.dirname(template)))
    tmpl = env.get_template(os.path.basename(template))
    return tmpl.render(hpc=subdomain is not None,
                       get_port=lambda name, val: get_port_number(subdomain, name, val))


def get_port_number(subdomain, port_name, default_value):
    """
    On HPC (ie: subdomain not None), query the port allocator service to get a port number for a given service
    On PC (ie: subdomain is None), just return the default value
    """
    if subdomain:
        r = requests.get('http://hpc-port-allocator.service/ports/{}/{}'.format(subdomain, port_name))
        r.raise_for_status()
        return int(r.text)
    else:
        return default_value


def modify_yaml(repo, tag, my_container=None, remove_loader_container=True):
    if not my_container:
        my_container = my_container_name()
    my_image = my_image_name()

    y = load_crane_yml()
    containers = y['containers']

    loader_container = get_loader_container(containers, my_image)
    if remove_loader_container:
        del containers[loader_container]

    tagged_loader_container = rename_container(loader_container, tag)
    add_repo_and_tag_to_images(containers, repo, tag)
    modify_images(containers, tagged_loader_container, my_container, tag)
    modify_links(containers, tagged_loader_container, my_container, tag)
    modify_volumes_from(containers, tagged_loader_container, my_container, tag)

    if 'groups' in y:
        modify_groups(y['groups'], tagged_loader_container, my_container, tag)

    with open(MODIFIED_YML_PATH, 'w') as f:
        f.write(yaml.dump(y, default_flow_style=False))


def get_loader_container(containers, my_image):
    loader_container = None
    for key, c in containers.iteritems():
        if c['image'] == my_image:
            if loader_container:
                raise Exception('crane.yml file has multiple containers that refer to {}'.format(my_image))
            loader_container = key

    if not loader_container:
        raise Exception('crane.yml contains no Loader image {}'.format(my_image))

    return loader_container


def add_repo_and_tag_to_images(containers, repo, tag):
    img_prefix = image_prefix(repo)
    img_suffix = image_suffix(tag)
    for key in containers.keys():
        c = containers.pop(key)
        c['image'] = '{}{}{}'.format(img_prefix, c['image'], img_suffix)
        c['original-name'] = key
        # Add the container back with the new container name
        containers[rename_container(key, tag)] = c


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
    pattern = compile('^([a-zA-Z0-9_-]+):')  # match any valid Docker container name followed by ':'
    replace = '{}\\1{}:'.format(my_container_prefix(), container_suffix(tag))
    loader_pattern = compile('^{}:'.format(tagged_loader_container))

    for c in containers.itervalues():
        if 'run' in c and 'link' in c['run']:
            links = [pattern.sub(replace, link) for link in c['run']['link']]
            c['run']['link'] = [loader_pattern.sub('{}:'.format(my_container), link) for link in links]


def modify_volumes_from(containers, tagged_loader_container, my_container, tag):
    """
    Add tag to all volumes-from links and replace links to the Loader container with my container name
    """
    # Replace links to Loader container with my container name
    loader_pattern = compile('^{}$'.format(tagged_loader_container))

    for c in containers.itervalues():
        if 'run' in c and 'volumes-from' in c['run']:
            volumes = [rename_container(link, tag) for link in c['run']['volumes-from']]
            c['run']['volumes-from'] = [loader_pattern.sub(my_container, link) for link in volumes]


def modify_groups(groups, tagged_loader_container, my_container, tag):
    """
    Add tag to all containers in groups and replace the Loader container with my container name
    """
    # Replace links to Loader container with my container name
    loader_pattern = compile('^{}$'.format(tagged_loader_container))

    for k in groups:
        groups[k] = [rename_container(c, tag) for c in groups[k]]
        groups[k] = [loader_pattern.sub(my_container, c) for c in groups[k]]


def modify_images(containers, tagged_loader_container, my_container, tag):
    """
    Perform any container touch-ups based on environment.
    """
    if my_subdomain():
        # Keep the mysql container light in HPC.
        # N.B. this assumes the default mysql config is light enough for HPC.
        pass
    elif psutil.virtual_memory().total >= 3 * 1024 * 1024 * 1024:
        # When running with more than 3GB of memory, we can afford to give a
        # bit more to MySQL.
        mysql_name = rename_container('mysql', tag)
        if mysql_name in containers:
            modify_image(
                containers[mysql_name]['image'],
                ('sed -i '
                 '"s/^innodb_buffer_pool_size.*$/innodb_buffer_pool_size = 1024M/g" '
                 '"/etc/mysql/my.cnf"'))


def rename_container(c, tag):
    """
    See also: patterns defined in modify_links()
    """
    return '{}{}{}'.format(my_container_prefix(), c, container_suffix(tag))

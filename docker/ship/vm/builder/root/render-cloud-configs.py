#!/usr/bin/python
import StringIO
import base64
import gzip
import os
import yaml

from jinja2 import Environment, FileSystemLoader, StrictUndefined
from os import walk
from os.path import join
from sys import argv

RESOURCES = '/resources'


def get_files(parent):
    """
    Return a map. The keys are all the file names under the given 'parent' folder. The values are
    the gzipped and base64 encoded file content.
    """
    file_list = []
    for root, _, files in walk(parent):
        for file_name in files:
            rel_file = file_name
            if root != parent:
                rel_dir = os.path.relpath(root, parent)
                rel_file = os.path.join(rel_dir, file_name)
            file_list.append(rel_file)


    file_map = {}
    for basename in file_list:
        file_map[basename] = get_file(join(parent, basename))
    return file_map


def get_file(path):
    with open(path) as f:
        out = StringIO.StringIO()
        with gzip.GzipFile(fileobj=out, mode='w') as gz:
            gz.write(f.read())
        return base64.b64encode(out.getvalue())


if __name__ == "__main__":
    """
    arg 1: path to ship.yml
    arg 2: path to the folder with extra files
    arg 3: output path to cloud-config.yml
    arg 4: output path to preload-cloud-config.yml
    arg 5: tag
    """

    env = Environment(
        loader=FileSystemLoader(RESOURCES),
        undefined=StrictUndefined,
        trim_blocks=True,
        lstrip_blocks=True)

    with open(argv[1]) as f:
        y = yaml.load(f)

    # Render cloud-config.yml
    with open(argv[3], 'w') as f:
        f.write(env.get_template('cloud-config.yml.jinja').render(
            hostname=y['hostname'],
            loader_image=y['loader'],
            swap_size=y['swap-size'],
            repo=y['repo'],
            target=y['target'],
            httpd_files=get_files(join(RESOURCES, 'httpd')),
            extra_files=get_files(argv[2]),
            configurable_registry=argv[6] == "1"
        ))

    # Render preload-cloud-config.yml
    with open(join(RESOURCES, 'preload-ssh.pub')) as f:
        PRELOAD_SSH_PUB = f.read()
    with open(argv[4], 'w') as f:
        f.write(env.get_template('preload-cloud-config.yml.jinja').render(
            preload_ssh_pub=PRELOAD_SSH_PUB
        ))

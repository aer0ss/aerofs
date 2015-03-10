#!/usr/local/bin/python

from jinja2 import Environment, FileSystemLoader, StrictUndefined
from os.path import join
from sys import argv
import yaml

RESOURCES = '/resources'

env = Environment(
    loader=FileSystemLoader(RESOURCES),
    undefined=StrictUndefined,
    trim_blocks=True,
    lstrip_blocks=True)


if __name__ == "__main__":
    """
    arg 1: path to ship.yml
    arg 2: output path to cloud-config.yml
    arg 3: output path to preload-cloud-config.yml
    arg 4: tag
    """
    with open(argv[1]) as f:
        y = yaml.load(f)

    # Render cloud-config.yml
    with open(argv[2], 'w') as f:
        f.write(env.get_template('cloud-config.yml.jinja').render(
            hostname=y['vm-host-name'],
            loader_image=y['loader-image'],
            swap_size=y['vm-swap-size'],
            repo=y['repo'],
            tag=argv[4],
            target=y['target'],
        ))

    # Render preload-cloud-config.yml
    with open(join(RESOURCES, 'preload-ssh.pub')) as f:
        PRELOAD_SSH_PUB = f.read()
    with open(argv[3], 'w') as f:
        f.write(env.get_template('preload-cloud-config.yml.jinja').render(
            preload_ssh_pub=PRELOAD_SSH_PUB
        ))

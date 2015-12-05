from common import TAG_PATH, call_crane, get_tag
from api import start
from crane_yml import modify_yaml, load_crane_yml


def load(repo_file, tag_file, target_file):

    # Verify that tag file content matches our own tag
    tag = get_tag()
    with open(tag_file) as f:
        tag_from_file = f.read().strip()
        # Use no tag if the provided file is empty
        if not tag_from_file:
            tag = ''
        if tag != tag_from_file:
            raise Exception("ERROR: Tag file content '{}' differs from the Loader's tag '{}'."
                            " Please check sanity of the sail script.".format(tag_from_file, tag))

    with open(repo_file) as f:
        repo = f.read().strip()
    with open(target_file) as f:
        target = f.read().strip()

    modify_yaml(repo, tag)
    call_crane('run', target)
    start(repo, target, repo_file, tag_file, target_file, tag)


def get_images():
    ret = set()
    containers = load_crane_yml()['containers']
    for key in containers:
        ret.add(containers[key]['image'])
    # Sort to help us monitor the progress of tasks that enumerate images.
    return sorted(ret)


def verify(loader_image):
    if not get_tag():
        raise Exception('{} is empty.'.format(TAG_PATH))

    has_loader_image = False
    for image in get_images():
        if ':' in image:
            raise Exception('Image name "{}" has a colon.'.format(image))
        if image == loader_image:
            has_loader_image = True

    if not has_loader_image:
        raise Exception('crane.yml contains no Loader image {}'.format(loader_image))

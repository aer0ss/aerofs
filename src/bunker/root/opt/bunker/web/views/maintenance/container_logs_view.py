import logging
from os.path import dirname, exists
from pyramid.view import view_config
import requests
from os import makedirs, unlink, close, write
from time import strftime
from zipfile import ZipFile
from subprocess import call
from loader_view import LOADER_URL
from logs_view import LOG_ARCHIVE_PATH
from tempfile import mkstemp

log = logging.getLogger(__name__)


@view_config(
    route_name='json-archive-container-logs',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def archive_container_logs(request):
    container2image = requests.get(LOADER_URL + '/containers').json()

    # Remove the old zip file
    parent = dirname(LOG_ARCHIVE_PATH)
    if not exists(parent):
        makedirs(parent)
    if exists(LOG_ARCHIVE_PATH):
        unlink(LOG_ARCHIVE_PATH)

    # Move it to a separate, background process if archiving takes too long.
    with ZipFile(LOG_ARCHIVE_PATH, 'w') as z:
        for container, image in container2image.iteritems():
            print 'Archiving logs for {}...'.format(container)
            fd, path = mkstemp()
            try:
                write(fd, 'Log collected at {} for container "{}" image "{}"\n'.format(strftime("%c"), container, image))
                # If the command may fail as the container doesn't exist, simply include error output in the log.
                call(['docker', 'logs', '--timestamps', container], stdout=fd, stderr=fd)
                close(fd)
                z.write(path, '{}.log'.format(container))
            finally:
                unlink(path)

    print 'Log archiving done.'
import glob
import json
from tempfile import NamedTemporaryFile
from os.path import dirname, exists, basename, splitext
from pyramid.view import view_config
from os import makedirs, unlink
from subprocess import check_output
from zipfile import ZipFile
from logs_view import LOG_ARCHIVE_PATH

@view_config(
    route_name='json-archive-container-logs',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def archive_container_logs(request):
    # Remove the old zip file
    parent = dirname(LOG_ARCHIVE_PATH)
    if not exists(parent):
        makedirs(parent)
    if exists(LOG_ARCHIVE_PATH):
        unlink(LOG_ARCHIVE_PATH)

    # Move it to a separate, background process if archiving takes too long.
    with ZipFile(LOG_ARCHIVE_PATH, 'w') as z:
        logfiles = glob.glob('/var/lib/docker/containers/*/*.log*')
        for path in logfiles:
            with open(path) as logfile, NamedTemporaryFile() as formatted_logs:
                print 'Archiving log at {}...'.format(path)
                container_id = basename(dirname(path))
                container_name = check_output(['docker', 'inspect', '--format="{{ .Name }}"', container_id]).strip().replace("/", "")

                for line in logfile.readlines():
                    # some docker log files have null bytes left over
                    logObject = json.loads(line.strip('\0'))
                    formatted_logs.write("{}:{}".format(logObject["time"], logObject["log"]))

                formatted_logs.flush()
                z.write(formatted_logs.name, "".join(tuple(container_name) + splitext(basename(path))[1:]))

    print 'Log archiving done.'

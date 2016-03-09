import glob
import json
import requests
import uuid
from tempfile import NamedTemporaryFile
from os.path import dirname, exists, basename, splitext
from pyramid.view import view_config
from os import makedirs, unlink
from zipfile import ZipFile
from logs_view import LOG_ARCHIVE_PATH
from docker import Client
from web.error import expected_error, unexpected_error
from web.version import get_private_version
from web import celery
from celery.task import task
from web.tasks import upload_log_files
from maintenance_util import get_conf

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

    cli = Client(base_url='unix://var/run/docker.sock', version='1.17')

    # Move it to a separate, background process if archiving takes too long.
    with ZipFile(LOG_ARCHIVE_PATH, 'w') as z:
        logfiles = glob.glob('/var/lib/docker/containers/*/*.log*')
        for path in logfiles:
            with open(path) as logfile, NamedTemporaryFile() as formatted_logs:
                print 'Archiving log at {}...'.format(path)
                container_id = basename(dirname(path))
                container_name = cli.inspect_container(container_id)['Name'].strip().replace("/", "")

                for line in logfile.readlines():
                    # some docker log files have null bytes left over
                    logObject = json.loads(line.strip('\0'))
                    formatted_logs.write("{}:{}".format(logObject["time"], logObject["log"]))

                formatted_logs.flush()
                z.write(formatted_logs.name, "".join(tuple(container_name) + splitext(basename(path))[1:]))

    print 'Log archiving done.'

@view_config(
    route_name='json-upload-container-logs',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def upload_container_logs(request):
    dryad_host = 'dryad.aerofs.com'
    cert_file  = '/etc/ssl/certs/aerofs_public_cacert.pem'

    # archive the logs
    archive_container_logs(request)

    # verify that the file exists
    if not exists(LOG_ARCHIVE_PATH):
        unexpected_error('AeroFS was unable to upload logs. Please try again later.')

    # verify that it is possible to reach the Dryad server.
    _test_dryad_connection(dryad_host, cert_file)

    # generate a defect ID
    defect_id = uuid.uuid4().hex

    # send an email - do this before uploading actually uploading the logs. This way, if emails
    # fail, we never have logs in dryad that we didn't get an email about.
    _send_log_collection_email(request, defect_id)

    # queue up the upload. The end user will get no notification if this fails, but we will have
    # the email to respond to.
    result = upload_log_files.delay(_dryad_upload_url(dryad_host, defect_id),
                                    cert_file,
                                    LOG_ARCHIVE_PATH)

    return {}

# Sends an email to AeroFS support with the defect ID using the CollectLogsServlet
def _send_log_collection_email(request, defect_id):

    url = _log_collection_url(request)

    payload = {
        'defectID': defect_id,
        'version':  get_private_version(request.registry.settings),
        'option':   'appliance',
        'subject':  request.params['subject'],
        'message':  request.params['message'],
        'email':    request.registry.settings.get('base.www.support_email_address')
    }

    r = requests.post(url, data=payload)

    if r.status_code != 200:
        unexpected_error(_error_message())

# The URL of the CollectLogsServlet
def _log_collection_url(request):
    return request.registry.settings["deployment.log_collection_server_uri"] + "/collect_logs"

# Tests the connection to the dryad server. If it cannot be reached, then abort, and encourage users
# to download the logs manually.
def _test_dryad_connection(host, cert):
    try:
        response = requests.get(_dryad_status_url(host),
                                verify='/etc/ssl/certs/aerofs_public_cacert.pem')
        if response.status_code != 200:
            _expected_dryad_connection_error()
    except requests.exceptions.RequestException:
        _expected_dryad_connection_error()

# logs an expected error with a message saying that the support servers are unreachable. This is
# expected because appliances could be isolated from the internet.
def _expected_dryad_connection_error():
    expected_error(_error_message())

# reusable error message instructing users to download logs when the appliance cannot connect to
# the support servers.
def _error_message():
    return 'AeroFS was unable to connect to the support servers. Please download logs manually.'

# dryad url to PUT logs to
def _dryad_upload_url(host, defectID):
    return "https://{}/v1.0/defects/{}/appliance".format(host, defectID)

# dryad url to GET status from
def _dryad_status_url(host):
    return "https://{}/v1.0/status".format(host)

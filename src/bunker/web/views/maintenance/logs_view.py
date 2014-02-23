##
## The view related to log file colllection
##
from datetime import datetime
from pyramid.response import Response
import logging
from pyramid.view import view_config
from maintenance_util import get_conf

log = logging.getLogger(__name__)

# The path is defined in BootstrapParam.java
LOG_ARCHIVE_PATH = '/opt/bootstrap/public/logs.zip'


@view_config(
    route_name='logs',
    permission='maintain',
    renderer='logs.mako'
)
def logs(request):
    _log_customer_id(request)
    return {}


@view_config(
    route_name='logs_auto_download',
    permission='maintain',
    renderer='logs_auto_download.mako'
)
def logs_auto_download(request):
    _log_customer_id(request)
    return {}


def _log_customer_id(request):
    log.info("customer id: {}".format(get_conf(request).get('customer_id', 'unknown')))


@view_config(
    route_name='download_logs',
    permission='maintain',
    request_method='GET'
)
def download_logs(request):
    """
    Return the log archive as the response. Call this method once archiving of
    log files is done.
    """

    return get_file_download_response(LOG_ARCHIVE_PATH, 'application/zip',
                               'aerofs-appliance-logs_', '.zip')


def get_file_download_response(file_system_path, mime_type, name_prefix,
                               name_suffix):
    """
    Return the HTTP Response for the browser to download the file specified in
    the path. The file name is name_prefix + <current time> + name_suffix.

    Also see
    http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/static_assets/files.html
    for alternatives to send file content as responses.
    """
    # The browser will use this name as the name for the downloaded file
    name = get_download_file_name(name_prefix, name_suffix)
    f = open(file_system_path)
    return Response(content_type=mime_type, app_iter=f,
                    content_disposition='attachment; filename={}'.format(name))


def get_download_file_name(name_prefix, name_suffix):
    return '{}{}{}'.format(name_prefix,
                           datetime.today().strftime('%Y%m%d-%H%M%S'),
                           name_suffix)

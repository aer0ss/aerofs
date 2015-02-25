import logging
import shutil
import os
import datetime
from dateutil import parser, tz
from pyramid.view import view_config
from maintenance_util import get_conf_client
from backup_view import BACKUP_FILE_PATH, example_backup_download_file_name

log = logging.getLogger(__name__)

@view_config(
    route_name='sync_settings',
    permission='maintain',
    renderer='sync_settings.mako'
)
def sync_settings_view(request):
    return {
        'example_backup_download_file_name': example_backup_download_file_name(),
        'modification_time' : get_conf_client(request).client_properties().get('properties.modification.time', '')
    }

def get_modification_time(request):
    utc_isoformat = get_conf_client(request).client_properties().get('properties.modification.time', '')
    if not utc_isoformat:
        return None
    # TODO (RD) do this on frontend, VM has no concept of local time zone
    # modification time is stored in UTC
    utc_datetime = dateutil.parser.parse(utc_isoformat).replace(tzinfo=dateutil.tz.tzutc())
    local_datetime = utc_datetime.astimezone(dateutil.tz.tzlocal())
    return local_datetime.strftime("%B %d %Y %I:%M%p")

@view_config(
    route_name='json_upload_externalproperties',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def upload_settings_backup(request):
    log.info("uploading backup file to sync external.properties")
    # Clean up old file
    if os.path.exists(BACKUP_FILE_PATH):
        os.remove(BACKUP_FILE_PATH)

    # See http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/forms/file_uploads.html
    input_file = request.POST['backup-file'].file
    input_file.seek(0)

    with open(BACKUP_FILE_PATH, 'wb') as output_file:
        shutil.copyfileobj(input_file, output_file)

    return HTTPOk()

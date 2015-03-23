import logging
from pyramid.view import view_config
from maintenance_util import get_conf_client, save_file_to_path
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

@view_config(
    route_name='json_upload_externalproperties',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def upload_settings_backup(request):
    log.info("uploading backup file to sync external.properties")

    # See http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/forms/file_uploads.html
    save_file_to_path(request.POST['backup-file'].file, BACKUP_FILE_PATH)
    return {}

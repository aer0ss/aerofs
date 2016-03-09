import requests
from celery.task import task
from web import celery
from web import log

# Uploads a file at a specified path to a given url that is authenticated with the given cert file
@celery.task(bind=True)
def upload_log_files(self, url, cert, log_file_path):
    # attempt to upload the file
    log.info("Uploading defect: {}".format(url))
    try:
        with open(log_file_path, 'rb') as f:
            log.info("Upload file path: {}".format(log_file_path))
            response = requests.put(url, data=f, verify=cert)
    # it is reasonable for this to fail because the file is no longer there, not available for
    # reading, or because there is some sort of connectivity issue with Dryad.
    except Exception as e:
        log.error("Failed to upload logs for to: {}. Retrying...".format(url))
        raise self.retry(exc=e, countdown=30, max_retries=40)

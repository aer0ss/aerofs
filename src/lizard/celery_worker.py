#!/usr/bin/env python
import lizard

# See: http://blog.miguelgrinberg.com/post/celery-and-the-flask-application-factory-pattern
#
# Run the Celery worker with:
#  $ cd ~/repos/aerofs/src/lizard
#  $ ./env/bin/celery worker -b "redis://" -A "celery_worker.celery" -l DEBUG

celery = lizard.celery

# Create the lizard Flask app and make it the current app, otherwise the tasks running inside the Celery worker
# won't have an app context.
app = lizard.create_app()
app.app_context().push()

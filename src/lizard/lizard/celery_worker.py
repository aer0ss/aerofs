import lizard
from celery.signals import task_prerun

# This file is used to bootstrap the Celery worker.
# In particular, it creates the Flask app so that Celery tasks have access
# to Flask configuration settings, sqlalchemy, etc...
#
# Consult Lizard's README for information on how to run the Celery worker.
#
# Inspired by: http://blog.miguelgrinberg.com/post/celery-and-the-flask-application-factory-pattern


# Create the lizard Flask app and make it the current app, otherwise the tasks running inside the
# Celery worker won't have an app context.
app = lizard.create_app()
app.app_context().push()

# The command line to run the Celery worker refers to this variable, so don't delete it, even though
# it looks like we don't use it in this script.
celery = lizard.celery


def before_task(**kwargs):
    # This method is executed before any celery task.

    # Important: remove the SQLAlchemy session before we start a task. Once a session is removed, a new session will
    # be created automatically. If we don't do that, SQLAlchemy will fail to see new objects in the DB.
    lizard.db.session.remove()


task_prerun.connect(before_task)

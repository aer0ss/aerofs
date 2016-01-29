import lizard

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

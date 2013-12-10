from flask import Flask
import os

app = Flask(__name__)
app.config.from_object('config')
# Also load overrides from a file referred to by an environment variable, if
# present.  Allows for dev settings to override production ones for local
# development.
if 'CONFIG_EXTRA' in os.environ:
    app.config.from_envvar('CONFIG_EXTRA')

from lizard import views

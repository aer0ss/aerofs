# This is a Flask wrapper for SegmentIO's analytics class to allow it to be
# initialized by configuration parameters from a Flask app.
# It is designed to behave like a proxy object for the underlying analytics client.

from analytics.client import Client as _Client

class AnalyticsClient(object):
    def __init__(self, app=None, **kwargs):
        self.client = None

        if app is not None:
            self.app = app
            self.init_app(app)
        else:
            self.app = None

    def init_app(self, app):
        app.config.setdefault("SEGMENTIO_WRITE_KEY", None)
        app.config.setdefault("SEGMENTIO_DEBUG", True)
        app.config.setdefault("SEGMENTIO_SEND", True)
        self.client = _Client(write_key=app.config["SEGMENTIO_WRITE_KEY"],
                              debug=app.config["SEGMENTIO_DEBUG"],
                              send=app.config["SEGMENTIO_SEND"]
                              )
        self.app = app

    def _check_app(self):
        assert self.app, "The flask_analytics extension was not registered to any applications. " \
                    "Please make sure to call init_app() first."
        assert self.client, "The flask_analytics extension was not registered to any applications. " \
                    "Please make sure to call init_app() first."
    def alias(self, *args, **kwargs):
        self._check_app()
        return self.client.alias(*args, **kwargs)
    def flush(self, *args, **kwargs):
        self._check_app()
        return self.client.flush(*args, **kwargs)
    def identify(self, *args, **kwargs):
        self._check_app()
        return self.client.identify(*args, **kwargs)
    def on_failure(self, *args, **kwargs):
        self._check_app()
        return self.client.on_failure(*args, **kwargs)
    def on_success(self, *args, **kwargs):
        self._check_app()
        return self.client.on_success(*args, **kwargs)
    def set_log_level(self, *args, **kwargs):
        self._check_app()
        return self.client.set_log_level(*args, **kwargs)
    def track(self, *args, **kwargs):
        self._check_app()
        return self.client.track(*args, **kwargs)


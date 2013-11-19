import views
import logging
from pyramid.request import Request
from license import is_license_present_and_valid
from util import is_private_deployment, is_configuration_initialized, is_maintenance_mode


class RedirectMiddleware(object):
    """
    This class exists to redirect the application to the setup page when the
    configuration system has not been initialized.
    """
    log = logging.getLogger(__name__)

    def __init__(self, application, settings):
        self.app = application
        self.settings = settings

    def noop(self, *args):
        self.app.complete = True

    def __call__(self, environ, start_response):
        if self._should_redirect(environ):
            self.log.info("redirect {} to maintenance page".format(environ['PATH_INFO']))
            response = self.app.invoke_subrequest(Request.blank(self._redirect_to()))
            return response(environ, start_response)

        return self.app(environ, start_response)

    def _should_redirect(self, environ):
        # Redirect all the pages to setup if:
        #  1. Private deployment, and
        #  2. Configuration has not been initialized (first run), or the license
        #     has expired, or the system is under maintenance, and
        #  3. The page is not a maintenance page.
        # See docs/design/pyramid_auth.md for more info.
        #
        # Note that static assets are served directly by nginx. We don't need to
        # consider them here.
        return is_private_deployment(self.settings) and \
                not self._is_configed_and_licensed_and_not_maintained() and \
                not self._is_maintenance_page(environ)

    def _is_configed_and_licensed_and_not_maintained(self):
        return is_configuration_initialized(self.settings) and \
               is_license_present_and_valid(self.settings) and \
               not is_maintenance_mode()

    def _is_maintenance_page(self, environ):
        # [1:] is to remove the leading slash
        return environ['PATH_INFO'][1:] in views.maintenance.routes

    def _redirect_to(self):
        return '/maintenance_mode' if is_maintenance_mode() else '/setup'
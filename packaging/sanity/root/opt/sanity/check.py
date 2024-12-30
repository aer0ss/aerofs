#!/usr/bin/python

import os
import re
import json
import cherrypy
import subprocess

class SanityCheck(object):
    # Returns [(boolean) is_healthy, (string) message].
    def perform_check(self, script_name):
        fully_qualified_script_name = '/opt/sanity/probes/' + script_name
        is_healthy = True
        message = ''
        try:
            subprocess.check_output(fully_qualified_script_name)
        except subprocess.CalledProcessError as e:
            is_healthy = False
            message = str(e.output).strip()
        return [is_healthy, message]

    def do_check(self, fail_hard):
        scripts = []
        for filename in os.listdir('/opt/sanity/probes'):
            match = re.match('.*\.sh', filename)
            if match:
                scripts.append(filename)
        statuses = []
        failure_occurred = False
        for script in scripts:
            service = script.split('.')[0]
            print(("Probing", service, "..."))
            status = self.perform_check(script)
            is_healthy = status[0]
            if not is_healthy:
                failure_occurred = True
            message = '' if len(status) == 1 or len(status[1]) == 0 else status[1]
            statuses.append({
                    'service': service,
                    'is_healthy': is_healthy,
                    'message': message
                })
        result = json.dumps({'statuses': statuses})
        if fail_hard and failure_occurred:
            cherrypy.response.status = 500
        return result

    def index(self):
        return self.do_check(fail_hard=True)
    index.exposed = True

    # Equivalent to / except that we do not throw a 500 in the case where a check fails. The 500
    # throw is convenient for / because it allows 3rd party integrations, e.g. nagios, to easily
    # detect failure conditions. It is not convenient however for rendering in bunker, hence why
    # we provide a soft failure interface.
    def soft_failure(self):
        return self.do_check(fail_hard=False)
    soft_failure.exposed = True

cherrypy.config.update({
        'server.socket_host': '0.0.0.0',
        'server.socket_port': 8000
    })

cherrypy.quickstart(SanityCheck())

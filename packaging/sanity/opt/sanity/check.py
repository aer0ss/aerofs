#!/usr/bin/python

import os
import re
import json
import cherrypy
import subprocess

class SanityCheck(object):

    # Returns [(boolean) is_healthy, (string) message].
    def performCheck(self, script_name):
        fully_qualified_script_name = '/opt/sanity/probes/' + script_name

        is_healthy = True
        message = ''
        try:
            subprocess.check_output(fully_qualified_script_name)
        except subprocess.CalledProcessError as e:
            is_healthy = False
            message = str(e.output).strip()

        return [is_healthy, message]

    def index(self):

        scripts = []
        for filename in os.listdir('/opt/sanity/probes'):
            match = re.match('.*\.sh', filename)

            if match:
                scripts.append(filename)

        statuses = []
        for script in scripts:
            service = script.split('.')[0]
            print "probing", service, "..."
            status = self.performCheck(script)
            print "done"

            if len(status) == 1 or len(status[1]) == 0:
                statuses.append({ \
                        'service': service, \
                        'is_healthy': status[0], \
                        'message': ''})
            else:
                statuses.append({ \
                        'service': service, \
                        'is_healthy': status[0], \
                        'message': status[1]})

        return json.dumps({'statuses': statuses})

    index.exposed = True

cherrypy.config.update({'server.socket_host': '0.0.0.0', \
        'server.socket_port': 8000})

cherrypy.quickstart(SanityCheck())

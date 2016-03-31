import psutil
import time

from flask import Flask
from flask_restful import Resource, Api
from threading import Thread


app = Flask(__name__)
api = Api(app)


class Stats(Resource):
    def get(self):
        return {
            'mem_usage_percent': psutil.virtual_memory().percent,
            'cpu_usage_percent': cpu_percent,
            'disk_usage_percent': psutil.disk_usage("/").percent,
        }


# Function that gets the CPU usage every 5 minutes.
def cpu_task():
    global cpu_percent
    cpu_percent = 0
    while True:
        # TODO (DS): Global vars not really the best option but since this is a 2 thread application
        # without any strict demand for absolute accuracy its okay for now.
        time.sleep(300)
        cpu_percent = psutil.cpu_percent()


api.add_resource(Stats, '/')

if __name__ == '__main__':
    cpu_thread = Thread(target=cpu_task)
    cpu_thread.daemon = True
    cpu_thread.start()
    app.run(debug=True, host='0.0.0.0')

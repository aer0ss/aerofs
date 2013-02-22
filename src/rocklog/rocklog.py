import datetime
import socket
import time
import base64
import simplejson
import requests
from flask import Flask
from flask import request
from flask import jsonify
from pyelasticsearch import ElasticSearch
from retrace_client import RetraceClient


ELASTIC_SEARCH_URL = 'http://localhost:9200'
CARBON_ADDRESS = ('metrics.aerofs.com', 2003)
MIXPANEL_URL = "http://api.mixpanel.com/track/"

EXPECTED_CONTENT_TYPE = 'application/json'
MESSAGE_KEY = '@message'
TIMESTAMP_KEY = '@timestamp'
METRICS_KEY = 'metrics'
EXCEPTION_KEY = 'exception'
VERSION_KEY = 'version'

app = Flask("rocklog")
app.config.from_pyfile("rocklog.cfg")
app.debug = True

es = ElasticSearch('http://localhost:9200/')

# assume that if the socket dies the flask app will die and gunicorn will restart it
carbon = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
carbon.connect(CARBON_ADDRESS)


class InvalidContentType(Exception):
    pass


@app.route("/")
def home():
    return success_response()


@app.route('/defects', methods=['POST'])
def defects():
    try:
        check_valid_request(request)
    except InvalidContentType as e:
        return error_response(415, 'not json')

    try:
        defect = request.json

        if EXCEPTION_KEY in defect:
            retracer = RetraceClient(50123, defect[VERSION_KEY])
            defect[EXCEPTION_KEY] = decode_exception(defect[EXCEPTION_KEY], retracer)
            retracer.close()

            if not MESSAGE_KEY in defect:
                # If no message for this defect, use the first line of the exception
                defect[MESSAGE_KEY] = defect[EXCEPTION_KEY].split('\n')[0]

        save_to_elasticsearch('defect', defect)

        # TODO: Return a defect number in the response to allow posting the logs for this defect at a later time
        return success_response()
    except Exception as e:
        app.logger.error("fail request err:%s" % e)
        raise


@app.route('/metrics', methods=['POST'])
def metrics():
    try:
        check_valid_request(request)
    except InvalidContentType as e:
        return error_response(415, 'not JSON')

    try:
        metric = request.json

        save_to_elasticsearch('metric', metric)
        save_to_graphite(metric)

        return success_response()
    except Exception as e:
        app.logger.error("fail request err:%s" % e)
        raise

@app.route('/events', methods=['POST'])
def events():
    try:
        check_valid_request(request)
    except InvalidContentType as e:
        return error_response(415, 'not JSON')

    try:
        event = request.json

        save_to_elasticsearch('event', event)
        save_to_mixpanel(event)

        return success_response()
    except Exception as e:
        app.logger.error("fail request err:%s" % e)
        raise

def check_valid_request(request):
    content_type = request.headers['Content-Type']

    if content_type != EXPECTED_CONTENT_TYPE:
        raise InvalidContentType('invalid content type exp:%s act:%s' % (EXPECTED_CONTENT_TYPE, content_type))


def save_to_elasticsearch(es_type, value):
    index_name = make_index(es_type + 's')
    es.index(index_name, es_type, value)


def make_index(index_prefix):
    now = datetime.datetime.utcnow()
    index_name = index_prefix + '-' + now.strftime('%Y-%m-%d')
    return index_name


def save_to_graphite(request_body):
    # common timestamp used for all metrics sent to graphite
    timestamp = request_body[TIMESTAMP_KEY]

    # an example of a date that comes from the java side: 2013-02-06T15:18:35.556-0800
    # NOTE: we send all times to RockLog in UTC
    # date format: %Y-%m-%dT%H:%M:%S (we can't support ms and strftime doesn't understand tz)

    utc_offset = timestamp[-4:]
    if utc_offset != "0000":
        raise RuntimeError("timestamp not utc o:" + utc_offset)

    epoch = int(time.mktime(time.strptime(timestamp[:-9], "%Y-%m-%dT%H:%M:%S")))

    # build out the string to send to graphite
    metrics = request_body[METRICS_KEY]
    graphite_data = ''.join(["%s %s %s\n" % (metric, value, epoch) for metric, value in metrics.items()])
    app.logger.debug(graphite_data)
    carbon.sendall(graphite_data)

def error_response(code, msg):
    resp = jsonify({'ok': False, 'status': code, 'message': msg})
    resp.status_code = code
    return resp


def success_response():
    resp = jsonify({'ok': True})
    resp.status_code = 200
    return resp


def decode_exception(exception, rc):
    """
    Takes a JSON exception object (with a stacktrace and possibly nested exceptions), unobfuscate the elements, and
    converts everything into a string.
    rc: RetraceClient instance
    """
    if not exception: return ""

    result = rc.retrace(exception['type'])['classname'] + ': ' + exception['message'] + '\n'
    result += '\n'.join([stackframe_to_string(frame, rc) for frame in exception.get('stacktrace', [])])
    if exception.get('cause', {}):
        result += '\n\nCaused by: ' + decode_exception(exception['cause'], rc)

    return result


def stackframe_to_string(frame, rc):
    r = rc.retrace(frame['class'], frame['method'], frame['line'])
    return r['classname'] + '.' + r['methodname'] + ':' + str(frame['line'])

def save_to_mixpanel(event):
    # We don't want event_name in the properties and as the event (it's redundant)
    name = event["event_name"]
    del event["event_name"]
    # We don't want @timestamp in the properties (too unique to be meaningful and mixpanel does its own timestamp)
    if TIMESTAMP_KEY in event: del event[TIMESTAMP_KEY]

    params = {
        "event": name,
        "properties": event
    }

    params["properties"]["token"] = app.config["MIXPANEL_TOKEN"]

    data = base64.b64encode(simplejson.dumps(params))

    requests.get(MIXPANEL_URL, params={"data":data})

if __name__ == "__main__":
    app.run()

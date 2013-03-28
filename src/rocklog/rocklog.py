import datetime
import socket
import time
import base64
import simplejson
import requests
import time
import MySQLdb
from flask import Flask
from flask import request
from flask import jsonify
from pyelasticsearch import ElasticSearch
from retrace_client import RetraceClient

ELASTIC_SEARCH_URL = 'http://localhost:9200'
CARBON_ADDRESS = ('metrics.aerofs.com', 2003)
HOSTED_GRAPHITE_ADDRESS = ('carbon.hostedgraphite.com', 2003)
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

if "MYSQLHOST" in app.config:
    mysql_enabled = True

# assume that if the socket dies the flask app will die and gunicorn will restart it
carbon = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
carbon.connect(CARBON_ADDRESS)
carbon.settimeout(.005)

hosted_graphite = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
hosted_graphite.connect(HOSTED_GRAPHITE_ADDRESS)
hosted_graphite.settimeout(.005)

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

        inject_user_data(defect)

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
        app.logger.error("fail request err:%s" % (e))
        raise

@app.route('/metrics', methods=['POST'])
def metrics():
    try:
        check_valid_request(request)
    except InvalidContentType as e:
        return error_response(415, 'not JSON')
    try:

        #
        # take the original data and save it to graphite
        #

        metric = request.json

        inject_user_data(metric)

        save_to_graphite(metric)

        #
        # move all the contents of the contained "metrics" json object
        # into the top-level json object
        #

        metrics = metric.pop(METRICS_KEY, [])
        for metric_key, metric_value in metrics.items():
            metric[metric_key] = metric_value

        #
        # now save this flattened object to elasticsearch
        #
        save_to_elasticsearch('metric', metric)

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

        inject_user_data(event)

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
    try:
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
        #graphite_data = ''.join(["%s %s %s\n" % (metric, value, epoch) for metric, value in metrics.items()])
        #carbon.sendall(graphite_data)
        graphite_data = ''.join(["%s %s %s\n" % ("5c5c66fc-6773-4008-a488-c889bdb60d9a." + metric, value, epoch) for metric, value in metrics.items()])
        hosted_graphite.sendall(graphite_data)
    except Exception as e:
        app.logger.error("fail socket err:%s" % e)

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

    if "message" in exception:
        message = exception['message']
    else:
        message = ""
    result = rc.retrace(exception['type'])['classname'] + ': ' + message + '\n'
    result += '\n'.join([stackframe_to_string(frame, rc) for frame in exception.get('stacktrace', [])])
    if exception.get('cause', {}):
        result += '\n\nCaused by: ' + decode_exception(exception['cause'], rc)

    return result


def stackframe_to_string(frame, rc):
    r = rc.retrace(frame['class'], frame['method'], frame['line'])
    return r['classname'] + '.' + r['methodname'] + ':' + str(frame['line'])

def inject_user_data(message):
    if not mysql_enabled:
        return
    try:
        user_id = message["user_id"]
        mysqldb = MySQLdb.connect(
                host=app.config["MYSQLHOST"],
                user=app.config["MYSQLUSER"],
                passwd=app.config["MYSQLPASSWORD"],
                db=app.config["MYSQLDB"])
        cur = mysqldb.cursor()
        cur.execute("select u_id_created_ts from sp_user where u_id=%s", (user_id))
        message["user_created_at"] = cur.fetchone()[0].isoformat()
        cur.close()
        mysqldb.close()
    except Exception as e:
        app.logger.warn(e)

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
    if "user_id" in params["properties"]:
        params["properties"]["distinct_id"] = params["properties"]["user_id"]

    data = base64.b64encode(simplejson.dumps(params))

    requests.get(MIXPANEL_URL, params={"data":data})

if __name__ == "__main__":
    app.run()

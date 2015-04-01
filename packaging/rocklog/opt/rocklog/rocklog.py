import datetime
import socket
import time
import simplejson
import time
from flask import Flask
from flask import request
from flask import jsonify
from pyelasticsearch import ElasticSearch

ELASTIC_SEARCH_URL = 'http://localhost:9200'
CARBON_ADDRESS = ('metrics.aerofs.com', 2003)

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
carbon = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
carbon.connect(CARBON_ADDRESS)
carbon.settimeout(.005)

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
            defect[EXCEPTION_KEY] = format_exception(defect[EXCEPTION_KEY])

            if not MESSAGE_KEY in defect:
                # If no message for this defect, use the first line of the exception
                defect[MESSAGE_KEY] = defect[EXCEPTION_KEY].split('\n')[0]

        save_to_elasticsearch('defect', defect)

        # TODO: Return a defect number in the response to allow posting the logs for this defect at a later time
        return success_response()
    except Exception as e:
        app.logger.error("fail request err:%s" % (e))
        raise

# TODO: remove this path. Keeping it here for clients that have not yet updated,
# no sense in turning a metrics report _into_ a defect by refusing it at the web server.
@app.route('/metrics', methods=['POST'])
def metrics():
    return success_response()

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

def error_response(code, msg):
    resp = jsonify({'ok': False, 'status': code, 'message': msg})
    resp.status_code = code
    return resp


def success_response():
    resp = jsonify({'ok': True})
    resp.status_code = 200
    return resp


def format_exception(exception, rc):
    """
    Takes a JSON exception object (with a stacktrace and possibly nested exceptions), and converts
    it into a string.
    """

    if not exception: return ""

    if "message" in exception:
        message = exception['message']
    else:
        message = ""

    result = exception['type'] + ': ' + message + '\n'
    result += '\n'.join([stackframe_to_string(frame, rc) for frame in exception.get('stacktrace', [])])

    if exception.get('cause', {}):
        result += '\n\nCaused by: ' + format_exception(exception['cause'], rc)

    return result


def stackframe_to_string(frame, rc):
    return str(frame['class']) + '.' + str(frame['method']) + ':' + str(frame['line'])

if __name__ == "__main__":
    app.run()

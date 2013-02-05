import datetime
import collections
from flask import Flask
from flask import json
from flask import request
from flask import jsonify
from pyelasticsearch import ElasticSearch
from retrace_client import RetraceClient

app = Flask("rocklog")
app.debug = True

es = ElasticSearch('http://localhost:9200/')

@app.route("/")
def home():
    return success_response()

@app.route('/metrics', methods = ['POST'])
@app.route('/defects', methods = ['POST'])
def defects():
    if request.headers['Content-Type'] != 'application/json':
        return error_response(415, "Not a JSON request")

    defect = request.json

    if 'exception' in defect:
        retracer = RetraceClient(50123, defect['version'])
        defect['exception'] = decode_exception(defect['exception'], retracer)
        retracer.close()

        if not '@message' in defect:
            # If no message for this defect, use the first line of the exception
            defect['@message'] = defect['exception'].split('\n')[0]

    # Save the defect into Elastic Search
    now = datetime.datetime.utcnow()
    index_name = "defects-" + now.strftime("%Y-%m-%d")
    es.index(index_name, 'defect', defect)

    # TODO: Return a defect number in the response to allow posting the logs for this defect at a later time
    return success_response()

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
        result += '\n\nCaused by: ' +  decode_exception(exception['cause'], rc)

    return result

def stackframe_to_string(frame, rc):
    r = rc.retrace(frame['class'], frame['method'], frame['line'])
    return r['classname'] + '.' + r['methodname'] + ':' + str(frame['line'])

if __name__ == "__main__":
    app.run()

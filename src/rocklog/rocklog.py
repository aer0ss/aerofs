import datetime
import collections
from flask import Flask
from flask import json
from flask import request
from flask import jsonify
from pyelasticsearch import ElasticSearch
from unobfuscator import Unobfuscator, ObfName

app = Flask("rocklog")
app.debug = False

es = ElasticSearch('http://localhost:9200/')
unobf = Unobfuscator(1000)

@app.route("/")
def home():
    return success_response()

@app.route('/defects', methods = ['POST'])
def defects():
    if request.headers['Content-Type'] != 'application/json':
        return error_response(415, "Not a JSON request")

    defect = request.json

    if 'exception' in defect:
        unobf.unobfuscate_and_cache(find_obfuscated_names(defect['exception']), defect['version'])
        defect['exception'] = decode_exception(defect['exception'], defect['version'])

        if not '@message' in defect:
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

def decode_exception(exception, version):
    """
    Takes a JSON exception object (with a stacktrace and possibly nested exceptions), unobfuscate the elements, and
    converts everything into a string.
    """
    if not exception: return ""

    result = exception['type'] + ': ' + exception['message'] + '\n'
    result += '\n'.join([unobf.get_unobfuscated(obfname, version) for obfname in obfnames_from_exception(exception)])
    if exception.get('cause', {}):
        result += '\n\nCaused by: ' +  decode_exception(exception['cause'], version)

    return result


def find_obfuscated_names(exception):
    """
    Traverses an exception object recursively and return a list with the obfuscated classes and methods
    """
    result = []

    if exception:
        result.append(ObfName(exception['type'], None, None))
        result.extend(obfnames_from_exception(exception))
        result.extend(find_obfuscated_names(exception.get('cause', {})))

    return result

def obfnames_from_exception(exception):
    return [ObfName(e['class'], e['method'], e['line']) for e in exception.get('stacktrace', [])]


if __name__ == "__main__":
    app.run()

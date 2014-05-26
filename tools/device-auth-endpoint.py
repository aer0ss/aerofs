"""
Example Device Authorization Endpoint

Test with curl:
curl -H "Content-Type: application/json" -i -X POST -d '{}' http://127.0.0.1:5000/device/v1.0/test@aerofs.com/authorized
"""

import pprint
from flask import Flask
from flask import jsonify
from flask import request

app = Flask(__name__)

def is_device_authorized(email, device_info):
    # TODO for the customer to implement
    pprint.pprint(device_info)
    return True

@app.route('/device/v1.0/<string:email>/authorized', methods = ['POST'])
def get_sysetm_authorized(email):
    return ('', 204) if is_device_authorized(email, request.json) else ('', 401)

if __name__ == '__main__':
    app.run(host='0.0.0.0', debug=True)

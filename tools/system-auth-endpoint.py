"""
Example System Authorization Endpoint

Test with curl:

curl -H "Content-Type: application/json" -i -X POST -d '{"interfaces": {"en0": {"ip": "192...", "mac": "abcde..."}, "en1": {"ip": "172...", "mac": "01234..."}, "en2": {"ip": "192...", "mac": "fafaf..."}}, "os": {"family": "Windows", "version": "XP", "patchlevel": "SP2"}}' http://127.0.0.1:5000/system/v1.0/test@aerofs.com/authorized
"""

from flask import Flask
from flask import jsonify
from flask import request

app = Flask(__name__)

def is_system_authorized(email, device_info):
    # TODO for the customer to implement
    return True

@app.route('/system/v1.0/<string:email>/authorized', methods = ['POST'])
def get_sysetm_authorized(email):
    return ('', 204) if is_system_authorized(email, request.json) else ('', 401)

if __name__ == '__main__':
    app.run(debug=True)

from flask import Flask, request, jsonify
from subprocess import check_output, Popen, PIPE
app = Flask(__name__)
IMAGE = 'aerofs/mysql'

@app.route("/get_code", methods=["GET"])
def get():
    user = request.args.get('userid')
    if not user: return "Must specify user id in the form of 'userid=email@address'", 400

    # Find mysql container
    container = None
    for line in check_output(['docker', 'ps']).split('\n'):
        if IMAGE in line:
            # last word is the container name
            container = line.strip().rsplit(' ', 1)[1]
            break
    if not container:
        return 'found no container running image {}'.format(IMAGE), 400

    # Run SQL in mysql container to print the last signup code of the given user
    script = 'mysql -N aerofs_sp <<< "select t_code from sp_signup_code where t_to=\'{}\' order by t_ts desc limit 1"'.format(user)
    cmd = ['docker', 'exec', container, 'bash', '-c', script]
    print cmd
    p = Popen(cmd, stdout=PIPE, stderr=PIPE)
    out, err = p.communicate()
    if err:
        return err, 400
    else:
        return jsonify(signup_code=out.strip())

app.run('0.0.0.0', 21337)

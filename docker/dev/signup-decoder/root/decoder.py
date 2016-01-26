
import MySQLdb
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route("/get_code", methods=["GET"])
def get():
    user = request.args.get('userid')
    if not user: return "Must specify user id in the form of 'userid=email@address'", 400

    try:
        db = MySQLdb.connect("mysql.docker", db="aerofs_sp")
        try:
            c = db.cursor()
            c.execute("select t_code from sp_signup_code where t_to=%s order by t_ts desc limit 1", [user])
            r = c.fetchone()
            if r is None:
                return "no such user: " + user, 404
            return jsonify(signup_code=r[0])
        except MySQLdb.Error as e:
            print e
            return e.str(), 400
        finally:
            db.close()
    except Exception as e:
        print e
        raise

@app.route("/get_all_codes", methods=["GET"])
def get_all():
    try:
        db = MySQLdb.connect("mysql.docker", db="aerofs_sp")
        try:
            c = db.cursor()
            c.execute("select t_to, t_code from sp_signup_code where t_ts in ( select max(t_ts) from sp_signup_code group by t_to )")
            rows = c.fetchall()
            entries = []
            for row in rows:
                entries.append({"user": row[0], "signup_code": row[1]})
            return jsonify(signup_codes=entries)
        except MySQLdb.Error as e:
            print e
            return e.str(), 400
        finally:
            db.close()
    except Exception as e:
        print e
        raise

app.run('0.0.0.0', 21337)

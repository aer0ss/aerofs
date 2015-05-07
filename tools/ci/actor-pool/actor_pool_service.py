#!/usr/bin/env python
import argparse
import logging
import threading
import time

from flask import Flask, request, json
import MySQLdb
import _mysql_exceptions
import requests

from unique_from_each import unique_from_each


app = Flask(__name__)
dblock = threading.RLock()
l = logging.getLogger(__name__)
CI_API_BASE_ADDR = 'https://192.168.128.197:8543/httpAuth/app/rest'


def garbage_collect_actors():
    while True:
        q = 'SELECT DISTINCT(build_id) AS build_id FROM actor WHERE free=0 ORDER BY build_id DESC'
        results = db_execute(q, []).fetchall()
        s = requests.Session()
        s.auth = ('aerofsbuild', 'temp123')
        s.headers.update({'Accept': 'application/json'})
        for result in results:
            build_id = result[0]
            r = s.get(CI_API_BASE_ADDR+'/builds/{}'.format(build_id), verify=False)
            if not r.json().get('running', False):
                l.info('gc: freeing the actors with build id: {}'.format(build_id))
                db_execute('UPDATE actor SET free=1 WHERE build_id=%s', [build_id])
        time.sleep(90)


def db_execute(querys, queryp):
    try:
        db = MySQLdb.connect(host="localhost",
                             user="actorpoolservice",
                             passwd="temp123",
                             db="actorpool")
        l.debug('about to acquire db lock')
        dblock.acquire()
        cur = db.cursor()
        l.debug("{} , {}".format(querys, queryp))
        cur.execute(querys, queryp)
        db.commit()
        return cur
    except:
        db.rollback()
        raise
    finally:
        l.debug('about to release db lock')
        dblock.release()
        db.close()


def get_actors():
    actors = request.get_json()["actors"]
    build_id = request.get_json()["build_id"]
    l.info("GET data: {}".format(request.get_json()))
    candidates = []

    # Do all queries atomically for deadlock avoidance. You get ALL actors or NO actors.
    with dblock:
        for actor in actors:
            vm = actor.get('vm')
            isolated = actor.get('isolated')
            os = actor.get('os')
            querys = "SELECT addr FROM actor WHERE free=1"
            queryp = []
            if vm is not None:
                querys += " AND vm=%s"
                queryp.append(vm)
            if isolated is not None:
                querys += " AND isolated=%s"
                queryp.append(isolated)
            if os is not None:
                querys += " AND os=%s"
                queryp.append(os)

            cur = db_execute(querys, queryp)
            fetched = cur.fetchall()
            if len(fetched) == 0:
                l.debug("Query yielded no results")
                return json.jsonify(message="Not Available", actors=None), 200
            else:
                candidates.append([t[0] for t in fetched])

        l.debug('Candidates: {}'.format(candidates))
        addresses = unique_from_each(candidates).get()

        if addresses is not None:
            querys = "UPDATE actor SET free=0, build_id=%s WHERE " + ' OR '.join('addr=%s' for _ in addresses)
            queryp = (build_id,) + tuple(addresses)
            db_execute(querys, queryp)

    return json.jsonify(message="OK", actors=addresses), 200


def register_actors():
    actors = request.get_json()
    l.info("POST data: {}".format(actors))
    for actor in actors:
        try:
            addr = actor['addr']
            vm = actor['vm']
            isolated = actor['isolated']
            os = actor['os']
        except KeyError:
            l.warn("POST: KeyError: addr:{} vm:{} isolated:{} os:{}".format(
                actor.get('addr'),
                actor.get('vm'),
                actor.get('isolated'),
                actor.get('os')))
            return json.jsonify(error="Missing required actor attributes"), 400

        querys = "INSERT INTO actor (addr, vm, isolated, os) VALUES (%s,%s,%s,%s)"
        queryp = (addr, vm, isolated, os)
        try:
            db_execute(querys, queryp)
        except _mysql_exceptions.IntegrityError as e:
            if e.args is not () and e.args[0] == 1062:
                # This is a duplicate entry error. The actor has already been registered.
                # Consider this to be a success, and return 200 OK
                l.debug('IntegrityError: ' + str(e))
            else:
                raise

    return json.jsonify(message="OK"), 200


@app.route('/', methods=['GET', 'POST'])
def application():
    status = 500
    content = json.jsonify(error='Internal Server Failure')
    try:
        if request.method == 'GET':
            content, status = get_actors()
        elif request.method == 'POST':
            content, status = register_actors()
    except Exception as e:
        l.error(e)
        content, status = json.jsonify(error=e.message), 500
    finally:
        return content, status


@app.route('/return', methods=['POST'])
def return_actor():
    status = 500
    content = json.jsonify(error='Internal Server Failure')
    try:
        addresses = request.get_json()
        l.info("POST data: {}".format(addresses))
        if len(addresses) > 0:
            querys = "UPDATE actor SET free=1 WHERE " + ' OR '.join('addr=%s' for _ in addresses)
            queryp = tuple(addresses)
            db_execute(querys, queryp)
            content, status = json.jsonify(message="OK"), 200
    except Exception as e:
        l.error(e)
        content, status = json.jsonify(error=e.message), 500
    finally:
        return content, status


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG, datefmt="%Y-%m-%d %H:%M:%S",
                        format="[%(asctime)s.%(msecs).03d] %(name)s %(levelname)s: %(message)s")
    host = '0.0.0.0'
    port = 8040
    parser = argparse.ArgumentParser(description="Actor pool service")
    parser.add_argument('--prod', dest='debug', action='store_false')
    args = parser.parse_args()
    l.info('Starting garbage collector in new thread')
    threading.Thread(target=garbage_collect_actors).start()
    l.info('Starting server at {}:{} with debug={}'.format(host, port, args.debug))
    app.run(host=host, port=port, debug=args.debug)

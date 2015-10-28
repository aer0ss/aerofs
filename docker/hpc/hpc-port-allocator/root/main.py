from flask import Flask, Response
import sqlite3
import logging

DATABASE = "state/database.db"
BASE_PORT = 2000  # The port number from which we start assigning ports

app = Flask(__name__)


def init_db():
    """
    Create the table on the database if needed
    """
    db = sqlite3.connect(DATABASE)
    cursor = db.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS ports (
            subdomain TEXT,
            service TEXT,
            port INTEGER UNIQUE,
            PRIMARY KEY (subdomain, service)) WITHOUT ROWID""")

    db.commit()
    db.close()


def get_port(cursor, subdomain, service):
    """
    Returns the port number for that subdomain and service, using `cursor` to query the DB.
    Returns None if there are no port set for that combination of subdomain / service.
    """
    result = cursor.execute('SELECT port FROM ports WHERE subdomain=? AND service=? LIMIT 1',
                            (subdomain, service)).fetchone()
    return result[0] if result else None


@app.route('/ports/<subdomain>/<service>')
def ports(subdomain, service):
    db = sqlite3.connect(DATABASE)
    cursor = db.cursor()
    port = get_port(cursor, subdomain, service)
    if not port:
        # Add the service in the DB with the next port number.
        cursor.execute('INSERT INTO ports values (?, ?, COALESCE((SELECT MAX(port) FROM ports), ?) + 1)',
                       (subdomain, service, BASE_PORT))
        db.commit()
        port = get_port(cursor, subdomain, service)

    db.close()

    return Response(str(port), content_type='application/json; charset=utf-8')


if __name__ == '__main__':
    init_db()
    app.logger.addHandler(logging.StreamHandler())
    app.run(host='0.0.0.0', port=80)


#!/usr/bin/python
# vim: set expandtab ts=2 sw=2:

import logging
import MySQLdb
import sys
import wsgiref
import wsgiref.simple_server
import urlparse
import json

logger = logging.getLogger("signup_code_server")

def get_signup_codes(userid, spconn):
  spcursor = spconn.cursor(MySQLdb.cursors.DictCursor)
  try:
    users = 0
    spcursor.execute("""SELECT * FROM sp_signup_code WHERE t_to = %s""", userid)
    signup_codes = []
    row = spcursor.fetchone()
    while row != None:
        signup_codes.append(row["t_code"])
        logger.debug(row["t_code"])
        row = spcursor.fetchone()
    return signup_codes
  except Exception as err:
    logger.warning('fail get user info err:%s' %err)
    raise err
  finally:
    spcursor.close()

def make_connection(user, host, dbname):
  logger.debug('make db connection %s@%s/%s' %(user, host, dbname))
  conn = MySQLdb.connect(user=user, host=host, db=dbname)
  return conn

def setup_logger():
  logging.basicConfig(level=logging.DEBUG)

config = {
    "sp_database": "localhost",
}

def application(environ, start_response):
    spconn = make_connection('aerofsdb', config["sp_database"], 'aerofs_sp')
    response_headers = [("Content-type", "text/plain")]
    status = "500 Internal Error"
    content = "Internal server failure"
    try:
        print "PATH_INFO:", environ["PATH_INFO"]
        print "SCRIPT_NAME:", environ["SCRIPT_NAME"]
        print "QUERY_STRING:", environ["QUERY_STRING"]
        args = urlparse.parse_qs(environ["QUERY_STRING"])
        userid = args["userid"][0]
        print "userid", userid
        signup_codes = get_signup_codes(userid, spconn)
        if len(signup_codes) == 0:
            content = "No signup codes for {0}".format(userid)
        else:
            print "signup_code", signup_codes
            content = json.dumps({ "signup_code": signup_codes[0] })
            status = "200 OK"
            print "content", content
    finally:
        start_response(status, response_headers)
        return content

if __name__ == '__main__':
    host = "0.0.0.0"
    port = 21337
    if len(sys.argv) >= 2:
        host = sys.argv[1]
    if len(sys.argv) >= 3:
        port = int(sys.argv[2])
    print "starting up {0} at {1}:{2}".format(sys.argv[0], host, port)
    server = wsgiref.simple_server.make_server(host, port, application)
    server.serve_forever()

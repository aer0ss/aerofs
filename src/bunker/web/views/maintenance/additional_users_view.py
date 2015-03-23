#TODO: (RD) remove this file once it's obsoleted by LDAP user sync to autocomplete table
import logging
import MySQLdb
import codecs

from maintenance_util import save_file_to_path, get_conf
from web.util import get_settings_nonempty
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPBadRequest

log = logging.getLogger(__name__)

ADDITIONAL_USERS_FILE_PATH = '/tmp/aerofs-extra-users.csv'
@view_config(
    route_name='autocomplete',
    permission='maintain',
    renderer='autocomplete.mako'
)
def autocomplete(request):
    return {}

@view_config(
    route_name='json_upload_additional_users',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def upload_additional_users(request):
    log.info("saving additional users file")
    save_file_to_path(request.POST['users-file'].file, ADDITIONAL_USERS_FILE_PATH)
    log.info("inserting additional users into database for autocomplete")

    try:
        users = parse_additional_users_file()
        properties = get_conf(request)
        save_additional_users_to_db(users,
                get_settings_nonempty(properties, "mysql.url", "localhost"),
                get_settings_nonempty(properties, "mysql.user", "aerofsdb"),
                properties["mysql.password"])
    except ValueError:
        log.warn("exception encountered while parsing additional users csv", exc_info=True)
        return HTTPBadRequest()
    return {}

def parse_additional_users_file():
    with codecs.open(ADDITIONAL_USERS_FILE_PATH, encoding='utf-8') as f:
        users = {}
        for line in f:
            fields = [field.strip() for field in line.split(",")]
            if len(fields) != 3:
                raise ValueError('each row of the csv file must contain the fields email, firstname, lastname')
            email = fields[0]

            if users.get(email, None):
                log.warn(u"user with email {} defined multiple times in input file, overriding previous values", email)

            users[email] = {
                'fullname': u"{} {}".format(fields[1], fields[2]),
                'lastname': fields[2],
            }
        return users

def save_additional_users_to_db(users, mysql_url, mysql_user, mysql_password):
    mysql_db = 'aerofs_sp'
    mysql_table = 'sp_autocomplete_users'
    split_url = mysql_url.split(":", 1)
    mysql_host = split_url[0]
    mysql_port = int(split_url[1]) if len(split_url) == 2 else 3306

    connection = MySQLdb.connect(host=mysql_host, port=mysql_port, user=mysql_user, passwd=mysql_password, db=mysql_db, charset='utf8')
    cursor = connection.cursor()

    insert_sql = """INSERT INTO {table_name}({email}, {fullname}, {lastname}) VALUES(%s, %s, %s);""" \
        .format(table_name=mysql_table, email='acu_email', fullname='acu_fullname', lastname='acu_lastname')
    try :
        # clears old table
        cursor.execute('DELETE FROM {};'.format(mysql_table))
        connection.commit()

        for email, values in users.iteritems():
            cursor.execute(insert_sql, (email, values['fullname'], values['lastname']))
        connection.commit()
    except:
        connection.rollback()
        connection.close()
        raise

    connection.close()


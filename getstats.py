#!/usr/bin/python
# vim: set expandtab ts=2 sw=2:

import sys
import MySQLdb as mdb
import argparse

class DBLogin:
    def __init__(self, user, host, passwd, db):
        self.user = user
        self.host = host
        self.passwd = passwd
        self.db = db

    def get_db_connection(self):
        return mdb.connect(**self.__dict__)
# end DBLogin

commands = {
    'help': 'list available commands and their details',
    'invite': 'list users that have used invite folderless',
    'share': 'list users that have shared folders',
    'exclude': 'list users that have used exclude from store',
    's3': 'list users that have activated s3',
    'join_store': 'list users that got an invite for a store',
    'join_store_ok': 'list users that accepted to join a store',
    'include': 'list users that have readmitted an excluded folder',
    'unlink': 'list users that have unlinked their device',
    'move_root': 'list users that have moved their root anchor'
}

sp_db_login = {
    'user': 'root',
    'host': 'sp.mysql.aws.aerofs.com',
    'passwd':  'WikQHpQjw2ugAl61',
    'db': 'aerofs_sp'
}

sv_db_login = {
    'user': 'root',
    'host': 'sp.mysql.aws.aerofs.com',
    'passwd': 'WikQHpQjw2ugAl61',
    'db': 'aerofs_sv'
}

def _create_command_parser():
    """Returns a Parser object to read the arguments for a command."""
    parser = argparse.ArgumentParser()
    parser.add_argument('-ns' , '-nostats', help='to not print any statistics', action='store_true')
    parser.add_argument('-nu', '-nousers', help='to not print any user ids', action='store_true')
    return parser

def _get_count_sv_event(cursor, ev_type):
    cursor.execute('select count(*) from sv_event where ev_type=%s', (ev_type))
    count = cursor.fetchone()[0]
    return int(count)

def _get_count_of_all_sv_events(cursor):
    cursor.execute('select count(*) from sv_event')
    count = cursor.fetchone()[0]
    return int(count)

def _get_sv_event_userinfo(cursor, ev_type):
    """Returns rows containing user_id and the count for how many times they generated the event"""
    cursor.execute("""select hdr_user, count(*) as c
                   from sv_event where ev_type=%s
                   group by hdr_user order by c desc""", (ev_type))
    return cursor.fetchall()

def _percentage(num, den):
    return float(float(num)/float(den))*100

def _print_sv_event_stats(cursor, ev_type):
    """Prints count, total count and percentage of the event"""
    c_count = _get_count_sv_event(cursor, ev_type)
    a_count = _get_count_of_all_sv_events(cursor)
    p = _percentage(c_count, a_count)
    print "Frequency of this event: %s out of %s, percent: ~%f" % (c_count, a_count, p)

def _print_sv_event_userinfo(cursor, ev_type):
    """Prints userid, count of event from user"""
    rows = _get_sv_event_userinfo(cursor, ev_type)
    for row in rows:
        userid = row[0]
        count = row[1]
        print "%s %s" % (userid, count)
    # endfor row

def _print_event_info(ev_type, opts):
    sv_login = DBLogin(**sv_db_login)
    svconn = sv_login.get_db_connection()
    svcursor = svconn.cursor()

    if not opts.ns:
        _print_sv_event_stats(svcursor, ev_type)
    # endif stat

    if not opts.nu:
        _print_sv_event_userinfo(svcursor, ev_type)
    # endif user

    svconn.close()

def help(opts):
    print "DESCRIPTION\n"
    print "\t Lists information about AeroFS from data available on SP and SV.\n"
    for cmd, desc in commands.items():
        print "\t\t %s -- %s\n" % (cmd, desc)
    # endfor
# end help()

def invite(opts):
    ev_type = '301' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end invite

def share(opts):
    ev_type = '300' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end share

def exclude(opts):
    ev_type = '1008' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end exclude

def s3(opts):
    ev_type = '1013' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end s3

def join_store(opts):
    ev_type = '1005' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end join_store

def join_store_ok(opts):
    ev_type = '1006' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end join_store_ok

def include(opts):
    ev_type = '1009' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end include

def unlink(opts):
    ev_type = '1011' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end unlink

def move_root(opts):
    ev_type = '1012' # defined in sv.proto
    _print_event_info(ev_type, opts)
# end move_root

if __name__ == '__main__':
    if len(sys.argv) == 1:
        print "usage: 'getstats help' for more details"
        sys.exit(1)
    cmd = sys.argv[1]
    if not cmd in commands:
        print "The command '%s' is not available, please type 'getstats help' for details" % (cmd)
    else:
        parser = _create_command_parser()
        opts = parser.parse_args(sys.argv[2:])
        locals()[cmd](opts)

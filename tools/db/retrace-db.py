from base64 import b64decode
import sqlite3 as lite
import sys

def getcrc32map(mapfile):
    map = {}
    with open(mapfile, 'r') as f:
        for line in f:
            vals = line.split(' ')
            crc32 = vals[0]
            name = str(b64decode(vals[1]))
            print name, crc32
            map[crc32] = name
        # endfor
    # endwith
    return map

def retrace(db, map):
    try:
        con = lite.connect(db)
        cur = con.cursor()
        cur.execute('SELECT o_s, o_o, o_n FROM o')
        rows = cur.fetchall()

        for row in rows:
            sid = row[0]
            oid = row[1]
            crc32 = row[2]

            name = crc32
            if crc32 in map:
                name = map[crc32]

            sql = 'UPDATE o SET o_o=? WHERE o_s=? AND o_o=?'
            args = name, sid, oid
            cur.execute(sql, args)
        # endfor rows
    except lite.Error, e:
        if con:
            con.rollback()
        print "Error %s:" % e.args[0]
        sys.exit(-2)
    finally:
        if con:
            con.close()
if __name__ == '__main__':
    if len(sys.argv) != 3:
        print "usage: python retrace-db.py <db> <mapfile>"
        sys.exit(-1)

    db = sys.argv[1]
    mapfile = sys.argv[2]

    map = getcrc32map(mapfile)
    retrace(db, map)

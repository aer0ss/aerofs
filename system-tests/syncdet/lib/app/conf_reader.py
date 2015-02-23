import sqlite3
import os
import time
import traceback


class ConfReader:
    """A class wrapping the conf db"""

    def __init__(self, db_path):
        assert os.path.exists(db_path)
        con = None
        n = 0
        # retry a few times in case of error
        # this really shouldn't be necessary but I/O through cygwin layer on Win7 vms
        # appears to be unreliable enough that we get the occasional failure...
        while ++n < 10:
            try:
                con = sqlite3.connect(db_path)

                for (k, v) in con.execute('select k,v from c'):
                    # N.B. this currently stores values as unicode
                    # as they are stored in the DB. Should this be converted to str?
                    self.__dict__[k] = v
                break
            except:
                # (JG) this was seen to happen just before the rtroot is deleted and recreated as
                # part of syncdet setup. The conf db was empty, which caused the query to fail. Since
                # the db is about to get nuked and recreated, it is OK to just close the connection and
                # continue.
                print 'Warning: error in reading from the conf db.'
                traceback.print_exc()
            finally:
                if con is not None:
                    con.close()
            time.sleep(0.1)


    @staticmethod
    def filename():
        """@return the filename of the conf file"""
        return 'conf'

#!/usr/bin/env python
import unittest

from aerofs_licensing import unicodecsv
from io import BytesIO

_EXPECTED_CSV_FILE_DATA = u"""Col1,Col2\r
\u2665,\u00DCnicode\r
""".encode('utf-8')

_HEADER = [u"Col1", u"Col2"]
_ROW = [u"\u2665", u"\u00DCnicode"]

_ROW_DICT = { "Col1": u"\u2665",
        "Col2": u"\u00DCnicode",
        }

class UnicodeCSVTestCase(unittest.TestCase):
    def test_can_read_unicode_csv(self):
        buf = BytesIO(_EXPECTED_CSV_FILE_DATA)
        r = unicodecsv.UnicodeReader(buf)
        header = r.next()
        row = r.next()
        self.assertTrue(header == _HEADER)
        self.assertTrue(row == _ROW)

    def test_can_write_unicode_csv(self):
        buf = BytesIO()
        w = unicodecsv.UnicodeWriter(buf)
        w.writerow(_HEADER)
        w.writerow(_ROW)
        self.assertTrue(buf.getvalue() == _EXPECTED_CSV_FILE_DATA)

    def test_can_read_unicode_to_dictionary(self):
        buf = BytesIO(_EXPECTED_CSV_FILE_DATA)
        r = unicodecsv.UnicodeDictReader(buf)
        d = r.next()
        self.assertTrue(r.fieldnames == _HEADER)
        self.assertTrue(d == _ROW_DICT)

    def test_can_write_unicode_from_dictionary(self):
        buf = BytesIO()
        w = unicodecsv.UnicodeDictWriter(buf, _HEADER)
        w.writeheader()
        w.writerow(_ROW_DICT)
        self.assertTrue(buf.getvalue() == _EXPECTED_CSV_FILE_DATA)

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

import unittest
from aerofs_ritual.id import SOID, unique_id_from_hexstring

class TestUniqueID(unittest.TestCase):
    """
    The tests o1_should_be_gt_o2_* gathered a number of sample oids from tests
    where we knew the alias and target object IDs
    N.B. in this definition of "greater than" we are assuming that the target
    object is greater than the alias object
    """

    def test_o1_should_be_gt_o2_1(self):
        self._assert_o1_gt_o2("8eb63241161240fcadca4f0d9ea60ca9",
                              "cc48dee247cb4cc4b5516223f3614d96")

    def test_o1_should_be_gt_o2_2(self):
        self._assert_o1_gt_o2("cef8d63d7b63454ca54fe3e84fd5e50c",
                              "e61656c8d68b4d568f2cfde7010302d3")

    def test_o1_should_be_gt_o2_3(self):
        self._assert_o1_gt_o2("aa640b24d8a340aea486168ed3bd615e",
                              "1773e9daa5d84fe1bb43597602c1d8a7")

    def test_o1_should_be_gt_o2_4(self):
        self._assert_o1_gt_o2("58c52d66ec4849fd8561ce9c680f3839",
                              "ad6d553691bc4e999ffbb70d182f7be1")

    def test_o1_should_be_gt_o2_5(self):
        self._assert_o1_gt_o2("6cb917a9e89b40419b6017f52cd62923",
                              "fc35b911e1c641f3b91a46332d4e1db9")

    def test_o1_should_be_gt_o2_6(self):
        self._assert_o1_gt_o2("af0e849662b1427b8eb04a21c870b36a",
                              "2fd663daf6894b3180fdddfc449ca2df")


    def _assert_o1_gt_o2(self, o1_hex, o2_hex):
        oid1 = unique_id_from_hexstring(o1_hex)
        oid2 = unique_id_from_hexstring(o2_hex)

        self.assertTrue(oid1 > oid2)
        self.assertFalse(oid1 < oid2)
        self.assertNotEqual(oid1, oid2)

class TestSOID(unittest.TestCase):

    def test_when_smaller_sidx_should_return_lt(self):
        oid = unique_id_from_hexstring("cc48dee247cb4cc4b5516223f3614d96")

        soid1 = SOID(1, oid)
        soid2 = SOID(2, oid)

        self.assertTrue(soid1 < soid2)
        self.assertFalse(soid1 > soid2)
        self.assertNotEqual(soid1, soid2)

    def test_when_larger_oid_but_smaller_sidx_should_return_gt(self):
        """
        An artifact of how SOID is implemented. Return the comparison of OIDs
        first, then compare SIndices
        """
        oid_lg = unique_id_from_hexstring("8eb63241161240fcadca4f0d9ea60ca9")
        oid_sm = unique_id_from_hexstring("cc48dee247cb4cc4b5516223f3614d96")

        self.assertTrue(oid_lg > oid_sm)

        soid1 = SOID(1, oid_lg)
        soid2 = SOID(2, oid_sm)

        self.assertTrue(soid1 > soid2)
        self.assertFalse(soid1 < soid2)
        self.assertNotEqual(soid1, soid2)

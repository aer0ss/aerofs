import struct
from binascii import unhexlify

def soid_from_pb(get_object_identifier_reply):
    oid = UniqueID(get_object_identifier_reply.oid)
    sidx = get_object_identifier_reply.sidx
    return SOID(sidx, oid)

def unique_id_from_hexstring(hexstring):
    return UniqueID(unhexlify(hexstring))

class SOID:
    def __init__(self, sidx, oid):
        assert isinstance(oid, UniqueID)
        self._sidx = sidx
        self._oid = oid

    def __eq__(self, other):
        return self._sidx == other._sidx and self._oid == other._oid

    def __lt__(self, other):
        return (self._oid < other._oid if not self._oid == other._oid else
                self._sidx < other._sidx)

    def __gt__(self, other):
        return not self.__lt__(other) and not self.__eq__(other)


class UniqueID:
    def __init__(self, barray):
        self._barray = bytearray(barray)

    def __eq__(self, other):
        assert len(other._barray) == len(self._barray)
        return self._barray == other._barray

    def __lt__(self, other):
        # Our Java implementation performs a signed-byte-by-byte comparison
        # from end of array to beginning. I do the same here
        for b_self, b_other in reversed(zip(self._barray, other._barray)):
            # Perform signed subtraction as in UniqueID.java
            i_self = self._byte_array_int_2_signed_int(b_self)
            i_other = self._byte_array_int_2_signed_int(b_other)
            diff = i_self - i_other
            if diff: return diff < 0
        return False

    def __gt__(self, other):
        return not self.__lt__(other) and not self.__eq__(other)

    def _byte_array_int_2_signed_int(self, ba_int):
        """
        @param ba_int: an integer between 0 and 255 inclusive
        @return: 8-bit signed interpretation of ba_int
        """
        return struct.unpack('b', struct.pack('B', ba_int))[0]
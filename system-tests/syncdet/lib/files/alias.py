"""
A module to generate files that are guaranteed to be target or alias objects.
Specifically, the user would call
   create_target_dir(path)
on the device which is supposed to have a target directory, and call
   create_alias_dir(path)
on the device which is supposed to have the alias dir
"""

from lib import ritual, id
import os

# OIDs are compared bytewise, and each byte is considered signed,
# so the 0 OID is in the middle of the OID space
_THRESHOLD_OID = id.unique_id_from_hexstring("00000000000000000000000000000000")

def create_target_dir(path):
    _create_dir_impl(path, lambda oid : oid > _THRESHOLD_OID )

def create_alias_dir(path):
    _create_dir_impl(path, lambda oid : oid < _THRESHOLD_OID )

def _create_dir_impl(path, _is_oid_correct_relative_to_threshold):
    """
    SIDE-EFFECTS: this method will create/delete a directory until the
    condition defined by _is_oid_correct_relative_to_threshold is met
    @param _is_oid_correct_relative_to_threshold: a boolean-returning function
    """
    assert os.path.exists(os.path.dirname(path))
    assert not os.path.exists(path)

    r = ritual.connect()

    os.mkdir(path)
    soid = id.soid_from_pb(r.test_get_object_identifier(path))

    while not _is_oid_correct_relative_to_threshold(soid._oid):
        os.rmdir(path)
        r.wait_path_to_disappear(path)
        os.mkdir(path)
        soid = id.soid_from_pb(r.test_get_object_identifier(path))

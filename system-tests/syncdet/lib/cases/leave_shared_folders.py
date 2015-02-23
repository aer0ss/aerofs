from aerofs_sp import sp as sp_service
from aerofs_common import exception
from aerofs_sp.gen.common_pb2 import PBException

def leave():
    sp = sp_service.connect()
    sp.sign_in()

    #
    # Cleanup shared folders for the local user:
    #
    # 1. leave all shared folders
    # 2. ignore all invitations (including those for shared folders we just left)
    #

    # TODO: be smart
    #   1. when multiple users, cleanup all of them
    #   2. spread sp calls across multiple actors
    #   3. pipeline sp calls on a single actor (multiple threads?)

    # TODO: figure out wtf is wrong with SP client and avoid sign_in between every call

    for sid in sp.list_shared_folders():
        print 'leave ', ''.join(["%02X" % ord(x) for x in sid]).strip()
        # (sigh) for some reason if two subsequent rpc are not separated by a sign_in the 2nd one fails with NO_PERM...
        sp.sign_in()
        try:
            sp.leave_shared_folder(sid)
        except exception.ExceptionReply as e:
            # ignore root sid
            if e.get_type() == PBException.BAD_ARGS:
                continue
            raise e

    # (sigh) for some reason if two subsequent rpc are not separated by a sign_in the 2nd one fails with NO_PERM...
    sp.sign_in()

    for inv in sp.list_pending_folder_invitations():
        print 'ignore ', ''.join(["%02X" % ord(x) for x in inv.share_id]).strip()
        # (sigh) for some reason if two subsequent rpc are not separated by a sign_in the 2nd one fails with NO_PERM...
        sp.sign_in()
        sp.ignore_shared_folder_invitation(inv.share_id)

spec = { 'entries': [leave] }

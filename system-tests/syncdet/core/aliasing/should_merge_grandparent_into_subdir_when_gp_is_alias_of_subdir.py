"""
The situation I really want to reproduce:

  init state:
  o1
   |_ o2
      |_ o3
          |_ o4 [this just indicates that o3 is a dir]

                 o1->o3, p(o3)=o2
                <----------------

 How to get there (on d2):
 (N.B. implicit to o1->o3 is that the directories under o1 and o3 are present)

d0          d1                 d2                  d3                 d4
--------- ------------ ------------------ --------------------- ----------------
                                            +instance_uniq_path
         | +no1       |                          +no3          |
       no1            |                no3                     |
      <----           |               <----                    |
 no1     |            |       no3        |                     |
         |            |                  |                     |
         |            |      +n2o2       |                      n2o2
keep a   |            |          -----------------------------------> no3
backup of|            |                  |                     |      n2o2
no1      |            |     n2o2         |                     |
         |            |       |_no3      |                     |
         |            |                  |                     |
         |            |                  |                     |
         |          no1                  |                     |
         |        ------>    no1         |                     |
         |        no3 maybe              |                     |
         |        <-------               |                     |
         |   n2o2     |                  |                     |
         |    |_no3   |   no1            |                     |
         |            |    |_n2o2        |                     |
         |            |       |_no3      |                     |
     <-------------------------------------------->            |
 no3     no1/no3      |                          no3           |
o1->o3   |            |                  |      o1->o3         |
         |            |                  |                  o1->o3
         |            |                  |                 ------->
         |            |                  |                     |     n2o2
         |            |                  |                     |      |_no3
         |            |                  |                     |      o1->o3
         |            |             o1->o3, p(o3)=o2           |
         |            |          <--------------------------------------
         |            |               n2o2, p(o2)=o1
         |            |          -------------------------------------->
         |            |                  |                     |     no3
         |            |   no3            |                     |      |_n2o2
         |            |    |_n2o2        |                     |      o1->o3
         |            |    o1->o3        |                     |
                                                               |
           ensure new dir tree received from d2                |

 final state:
                          no3
                           |_n2o2
                           o1->o3

"""
import os
import shutil
from os.path import join

from syncdet.case import sync, actor_id
from syncdet.case.assertion import assertTrue, assertEqual

from lib import ritual
from lib.files import dirtree, instance_unique_path, wait_dir, alias
from lib.network_partition import NetworkPartition


_SYNCED_INSTANCE_UNIQUE_PATH = "synced instance unique path"
_START_PARTITIONS = "start partitions"
_ALL_PARTITION_1 = "all partition 1"
_ALL_PARTITION_2 = "all partition 2"
_ALL_PARTITION_3 = "all partition 3"
_ALL_PARTITION_4 = "all partition 4"
_D0_TO_D3 = "d0<->no1/no3<->d3"
_D0D3_TO_D4 = "d0d3->o1->o3->d4"
_ALL_PARTITION_5 = "all partition 5"
_D2_RESOLVED_ALIAS_CHAIN = "d2 resolved alias chain"
_ALL_PARTITION_6 = "all partition 6"

_nc = "folder_nc"
_n2 = "folder2"

# Resume sync takes a very long time to restore AntiEntropy, this is
# unfortunately needed to avoid timeouts
_SYNC_TIMEOUT = 180

def d0():
    assertEqual(0, actor_id())
    _wait_to_sync_root_folder()
    with NetworkPartition():
        sync.sync(_START_PARTITIONS)
        sync.sync(_ALL_PARTITION_1, timeout=_SYNC_TIMEOUT)

    wait_dir(join(instance_unique_path(), _nc))
    sync.sync_next(0)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_2, timeout=_SYNC_TIMEOUT)
        sync.sync(_ALL_PARTITION_3, timeout=_SYNC_TIMEOUT)

        # Create a subdir of n3 to help wait for aliasing to complete
        os.mkdir(join(instance_unique_path(), _nc, _subdir_name()))

        sync.sync(_ALL_PARTITION_4)

    dt = dirtree.InstanceUniqueDirTree(_create_aliased_dirtree_dict())
    dirtree.wait_for_any(dt)

    # d0 and d3 have performed aliasing of no1 and no3
    # Let device 4 know they can grab the alias information
    sync.sync(_D0_TO_D3, [3,4], timeout=_SYNC_TIMEOUT)

    # Wait until d4 has the alias information
    sync.sync(_D0D3_TO_D4, [3,4], timeout=_SYNC_TIMEOUT)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_5, timeout=_SYNC_TIMEOUT)
        sync.sync(_D2_RESOLVED_ALIAS_CHAIN)

    _wait_for_final_state()
    sync.sync(_ALL_PARTITION_6)


def d1():
    assertEqual(1, actor_id())
    _wait_to_sync_root_folder()
    with NetworkPartition():
        sync.sync(_START_PARTITIONS)
        alias.create_alias_dir(join(instance_unique_path(), _nc))
        sync.sync(_ALL_PARTITION_1, timeout=_SYNC_TIMEOUT)

    sync.sync_prev(0)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_2, timeout=_SYNC_TIMEOUT)
        sync.sync(_ALL_PARTITION_3, timeout=_SYNC_TIMEOUT)

    assertTrue(dirtree.InstanceUniqueDirTree({_nc : {}}).represents_fs())
    sync.sync_next(0)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_4)
        sync.sync(_ALL_PARTITION_5, timeout=_SYNC_TIMEOUT)
        sync.sync(_D2_RESOLVED_ALIAS_CHAIN)

    _wait_for_final_state()
    sync.sync(_ALL_PARTITION_6, timeout=_SYNC_TIMEOUT)


def d2():
    assertEqual(2, actor_id())

    _wait_to_sync_root_folder()

    sync.sync(_START_PARTITIONS)

    wait_dir(join(instance_unique_path(), _nc))
    sync.sync_next(0, timeout=_SYNC_TIMEOUT)

    r = ritual.connect()

    with NetworkPartition(r):
        sync.sync(_ALL_PARTITION_1, timeout=_SYNC_TIMEOUT)
        os.mkdir(join(instance_unique_path(), _n2))
        sync.sync(_ALL_PARTITION_2, timeout=_SYNC_TIMEOUT)

    # Wait for d4 to receive n2o2
    sync.sync_next(0, 4, timeout=_SYNC_TIMEOUT)

    with NetworkPartition(r):
        # Nest the name-conflict dir under n2: ie n2o2->no3
        _mv_nc_dir_under_n2()
        sync.sync(_ALL_PARTITION_3)

    # Wait for d1 to share _nc here
    dt = dirtree.InstanceUniqueDirTree({_n2 : {_nc :{}},
                                        _nc : {}})
    dirtree.wait_for_any(dt)

    sync.sync_prev(0)

    with NetworkPartition(r):
        sync.sync(_ALL_PARTITION_4)
        _d2_create_directory_chain(r, dt)
        sync.sync(_ALL_PARTITION_5, timeout=_SYNC_TIMEOUT)

    _wait_for_final_state()

    # Signal that d4 should hide before letting other peers receive final state
    # from d2
    sync.sync_next(1, 4)

    # Signal to all devices that d2 resolved the alias chain
    sync.sync(_D2_RESOLVED_ALIAS_CHAIN)

    # Sanity check to ensure that the "final state" didn't change on d2
    _wait_for_final_state()
    sync.sync(_ALL_PARTITION_6, timeout=_SYNC_TIMEOUT)

def _d2_create_directory_chain(r, initial_dt):
    """
    While partitioned, create the no1->n2o2->no3 dir structure.
    NB the to-be alias object should be the highest ancestor of this dir.
    The alias will have the lower OID among o1 and o3
    @param r: ritual client
    """

    # Ensure that we're starting from the expected directory layout
    assertTrue(initial_dt.represents_fs())

    o1 = r.test_get_object_identifier(join(
        instance_unique_path(), _nc))
    o3 = r.test_get_object_identifier(join(
        instance_unique_path(), _n2, _nc))

    shutil.move(join(instance_unique_path(), _n2),
        join(instance_unique_path(), _nc))

    # Sanity check
    assertEqual(o1, r.test_get_object_identifier(
        join(instance_unique_path(), _nc)))

    assertEqual(o3, r.test_get_object_identifier(
        join(instance_unique_path(), _nc, _n2, _nc)))

    final_dt = dirtree.InstanceUniqueDirTree({_nc: {_n2: {_nc: {}}}})
    assertTrue(final_dt.represents_fs())


def d3():
    assertEqual(3, actor_id())

    os.mkdir(instance_unique_path())

    # Wait til all devices received the instance unique path
    sync.sync(_SYNCED_INSTANCE_UNIQUE_PATH)

    # Wait til all relevant devices hid in a barrier
    with NetworkPartition():
        # Create the target path in a network partition so that all invalid
        # files are not shared with other peers
        alias.create_target_dir(os.path.join(instance_unique_path(), _nc))
        sync.sync(_START_PARTITIONS)

    sync.sync_prev(0, timeout=_SYNC_TIMEOUT)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_1, timeout=_SYNC_TIMEOUT)
        sync.sync(_ALL_PARTITION_2, timeout=_SYNC_TIMEOUT)
        sync.sync(_ALL_PARTITION_3, timeout=_SYNC_TIMEOUT)

        # Create a subdir of n3 to help wait for aliasing to complete
        os.mkdir(join(instance_unique_path(), _nc, _subdir_name()))

        sync.sync(_ALL_PARTITION_4)

    dt = dirtree.InstanceUniqueDirTree(_create_aliased_dirtree_dict())
    dirtree.wait_for_any(dt)

    # d0 and d3 have performed aliasing of no1 and no3
    # tell device 4 they can receive the alias info now
    sync.sync(_D0_TO_D3, [0,4], timeout=_SYNC_TIMEOUT)

    # Wait until d4 has the alias information
    sync.sync(_D0D3_TO_D4, [0,4], timeout=_SYNC_TIMEOUT)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_5, timeout=_SYNC_TIMEOUT)
        sync.sync(_D2_RESOLVED_ALIAS_CHAIN)

    _wait_for_final_state()
    sync.sync(_ALL_PARTITION_6, timeout=_SYNC_TIMEOUT)

def d4():
    assertEqual(4, actor_id())

    _wait_to_sync_root_folder()

    with NetworkPartition():
        sync.sync(_START_PARTITIONS)
        sync.sync(_ALL_PARTITION_1, timeout=_SYNC_TIMEOUT)
        sync.sync(_ALL_PARTITION_2, timeout=_SYNC_TIMEOUT)

    dt = dirtree.InstanceUniqueDirTree({_n2 : {}, _nc : {}})
    dirtree.wait_for_any(dt)

    # Signal that the n2o2 dir was received
    sync.sync_prev(0, 2)

    with NetworkPartition():
        sync.sync(_ALL_PARTITION_3)
        sync.sync(_ALL_PARTITION_4)
        # Wait until devices 0 and 3 have performed aliasing
        sync.sync(_D0_TO_D3, [0,3], timeout=_SYNC_TIMEOUT)

    # Grab the alias information from device 0 or 3
    dict = _create_aliased_dirtree_dict()
    dict[_n2] = {}
    dt = dirtree.InstanceUniqueDirTree(dict)
    dirtree.wait_for_any(dt)

    # Signal that d4 has the alias information
    sync.sync(_D0D3_TO_D4, [0,3])

    with NetworkPartition():
        _mv_nc_dir_under_n2()
        sync.sync(_ALL_PARTITION_5, timeout=_SYNC_TIMEOUT)

    # Wait until device 2 indicates it has resolved the alias chain,
    # then this device should hide so that other devices get update info
    # from device 2
    sync.sync_prev(1,2)

    with NetworkPartition():
        sync.sync(_D2_RESOLVED_ALIAS_CHAIN)
        sync.sync(_ALL_PARTITION_6, timeout=_SYNC_TIMEOUT)

    _wait_for_final_state()

def observers():
    with NetworkPartition():
        sync_ids = (_START_PARTITIONS, _ALL_PARTITION_1, _ALL_PARTITION_2,
            _ALL_PARTITION_3, _ALL_PARTITION_4, _ALL_PARTITION_5,
            _D2_RESOLVED_ALIAS_CHAIN, _ALL_PARTITION_6)
        for sync_id in sync_ids:
            sync.sync(sync_id, timeout=_SYNC_TIMEOUT)

    _wait_for_final_state()

def _wait_for_final_state():
    # Final state is the contents of the aliased dirtree, plus _n2 under _nc
    dt_dict = _create_aliased_dirtree_dict()
    dt_dict[_nc][_n2] = {}
    dt = dirtree.InstanceUniqueDirTree(dt_dict)
    dirtree.wait_for_any(dt)

def _wait_to_sync_root_folder():
    dirtree.wait_for_any(dirtree.InstanceUniqueDirTree({}))
    sync.sync(_SYNCED_INSTANCE_UNIQUE_PATH)

def _subdir_name(): return "subdir" + str(actor_id())

def _create_aliased_dirtree_dict():
    """
    @return: the dictionary of the expected dirtree after aliasing n1 and n3
    """
    return  {_nc : { "subdir0":{},
                     "subdir3":{} }}

def _mv_nc_dir_under_n2():
    r = ritual.connect()
    o_nc = r.test_get_object_identifier(join(instance_unique_path(), _nc))
    shutil.move(join(instance_unique_path(), _nc),
        join(instance_unique_path(), _n2))
    assertEqual(o_nc, r.test_get_object_identifier(
        join(instance_unique_path(), _n2, _nc)))

# Because of resume_sync, this test can take a very long time to pass
spec = {'entries':[d0,d1,d2,d3,d4], 'default':observers, 'timeout':6*60}

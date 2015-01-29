An invariant: for any given OID, there may be multiple objects of this OID across multiple stores in the local system. However, at any given time, there must be at most one of these objects whose resync flag is unset.
**Device originates migration of file O from origin store S to target store T**
```Halt if O is unsync in either S or TCopy all branches and their associated versions, from S to T KML = KML in S + KML in T - sum of all local versionsfor <OCID, DID, TICK> in all the newly recorded ticks { 
    add to the V table    add to the IV table}
Move O in S to trash with a special name to indicate the emigration, AND increment the version of O’s meta in S
```**Device downloads the metadata of file O in store T, causing O to be created or become from expelled to admitted in T**

```If (O’s OID is found in another store S) {    Perform the same procedure as in the previous section}
```Reasoning about using the same procedure: Incrementing the version of O’s meta introduces O(n2) communication overhead as every peer has to generate a new version. However, it is needed to eliminate inconsistency in the following and other similar scenarios:
Initial state:
- Peer A has store S and T
- Peer B has store T
- Peer C has store S and T
- Peer D has store S
Sequence:
1. A initiates migration of O from S to T2. B receives the update from A, and thus creates O in T3. A goes offline4. C receives the update from B, and thus applies the migration. Note that C doesn’tincrement O’s version in S after moving it to trash
Now, because D and A never communicate, D doesn’t know about the update of O in S, and therefore is inconsistent with C on S.
**Device notices deletion of object O from S, with a new name that signifies migration of O to T**
```
if T doesn't exist {    ask the peer about T if T can be created {        create T 
    }}
if T doesn’t exist { 
    delete O} else if O is a file {    download O in T from the sending device (causing emigration of O with the procedure mentioned in the previous section)
} else {    download all the children under O in S, ignoring errors (causing recursive calls to this procedure). This is necessary to make sure children are emigrated before deleting the folder.}
```**Device receives immigrant order via GetVersReply**
```for <OCID, DID, TICK, ORDER_DID, ORDER_TICK> in all the tuples being received:
    if (row <OCID, DID, TICK> doesn’t exist in the IV table):        record it in the IV table        record it in the V table // assert it’s not in V yet
```
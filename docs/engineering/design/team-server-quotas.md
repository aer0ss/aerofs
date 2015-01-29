# Customer Requirements

* The Administrator can specify a global per-user quota (e.g. 100gb)
through the AeroFS Appliance Admin panel.
* Quota enforcement will only be performed on the Team Server.
* No quota Enforcement will be performed on peer-to-peer syncing
between other AeroFS devices.
* Quotas are applied as follows:
  * Files placed in a user's AeroFS folder that are synced to the Team Server
    count towards quota
  * If a user A shares a folder F with user B, and user B accepts it, and that
    folder is synced to the Team Server, the folder F will count against both
    user A's and user B's quota
* When a user's quota is 0, syncing to the Team Server will be paused
  until files from synced folders are deleted such that the quota goes
  above 0.
* Users are notified through AeroFS that their quota usage is above a
  certain threshold (e.g. 80%).
* Administrators are notified through the AeroFS audit log (or
  equivalent) that a given user's quota is above a certain threshold
  (e.g. 80%).
* Administrators can query a user's quota and usage through the API.

# Design

* An API call will be added to Sparta. The daemon uses it to inform Sparta its current data usage for each store. In response, Sparta tells the daemon whether or not to stop collecting file contents for these stores.

      definition:
          POST /quota
     
      request body (pseudo): {
          stores: [
              { 'sid': '1a2b3c', 'bytes_used': 12345 },
              { 'sid': '2b3c4d', 'bytes_used': 1234 },
              { 'sid': '3c4d5e', 'bytes_used': 123456 },
          ]
      }

      response body (pseudo): {
          store_actions: [
              { 'sid': '1a2b3c', 'collect_content': true },
              { 'sid': '1a2b3c', 'collect_content': false },
              { 'sid': '1a2b3c', 'collect_content': true },
          ]
      }

* The daemon calls this method immediately after launching, and then continues calling at a fixed time interval. It stops and restarts content collection based on Sparta's response.

* If a call fails, the daemon continues operation based on the previous response. If the first call fails, the daemon starts collection on all the stores (to optimize availability over quota enforcement).

* Sparta provides all the intelligence: It computes per-user quota across stores and multiple TSes, sends users email notifications, and logs quota usage to the audit log.

* To determine if a given store `s` should stop content collection:

        for user in [ s for s in store.users() if not s.isTSUser() ]:
           if sum([s.usage() for s in user.stores()]) < quota - headroom: return 'start'
        return 'stop'
        
  where `headroom` is an empirically determined number to reduce the chance the daemon goes over quota tween two API calls.

* Theoretically, Sparta needn't persist quota usage to disk. In practice, persisting the data helps Sparta's calculation when it restarts, before all the daemons report quota usage after the restart.


## Future refinements

* The above formula can be refined either manually or automatically to optimize for certain statistical patterns of quota usage.

* Even though the above formula reserves a headroom, a store can still go over quota between two API calls. Therefore, as the daemon approaches quotas, Sparta can ask it in the API response to increase the call's frequency. In the most aggressive (and inefficient) manner, Sparta can ask the daemon to report on every content download.

* Alternatively or additionally, Sparta can tell the daemon in the API response the estimated remaining quota left for a store, so the daemon can avoid downloading files that are bigger than that. The estimated remaining quota `erq` for store `s` can be calculated as:

        erq = quota
        for user in [ u for u in store.users() if not u.isTSUser() ]:
           erq = min(erq, quota - sum([s.usage() for s in user.stores()]))
        return erq
        
# Notes on implementation

* Ideally we should expose the REST API through Sparta. However, because we use device-based certificate for daemon communications, the API will be exposed by SP as protobuf.

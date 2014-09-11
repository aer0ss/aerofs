# Polaris

Polaris is the AeroFS centralized metadata server. It is built on top of baseline.

# API

GET  /changes/{oid}?since={start_logical_timestamp_exclusive}&count={returned_result_count}
POST /batch



# Bugs

* Publish notifications via verkehr
* Include reasons for update failure in response entities
* Include JSON parsing failure issues in response entities

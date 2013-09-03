# A set of default parameters for persistent, which can be overridden with
# appropriate configuration passed to persistent::services.
class persistent::params {
  $mysql_bind_address = '0.0.0.0'
  $redis_bind_address = 'all'
}

# == Class: redis-diskstore
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis::diskstore inherits redis {
    # Needs to be a subdirectory of the existing database dir.
    $redis_store_dir = "${redis_database_dir}/store"

    # Annoying that this is specified as a string, have to parse it.
    $mem = inline_template("<%
        mem,unit = scope.lookupvar('::memorysize').split
        mem = mem.to_f
        # Normalize mem to KiB
        case unit
            when nil:  mem *= (1<<0)
            when 'kB': mem *= (1<<10)
            when 'MB': mem *= (1<<20)
            when 'GB': mem *= (1<<30)
            when 'TB': mem *= (1<<40)
        end
        %><%= mem.to_i %>")

    $redis_cache_max_memory = $mem / 2

    servlet::config::file{"/etc/redis/redis.conf":
        content => template(
            "redis/diskstore.conf.erb"
        ),
        require => Package["aerofs-redis-server"],
        notify  => Service["redis-server"]
    }
}

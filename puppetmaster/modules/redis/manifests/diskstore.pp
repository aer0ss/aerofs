class redis::diskstore (
    $redis_bindaddr = $redis::redis_bindaddr
) inherits redis {

    # Variables for inherited class templates.
    $redis_port = 6380
    $redis_conf_filename = "/etc/redis/redis-diskstore.conf"
    $redis_pidfile = "/run/redis-diskstore.pid"
    $redis_logfile = "/var/log/redis/redis-diskstore.log"

    # Needs to be a subdirectory of the existing database dir.
    $redis_store_dir = "${redis_database_dir}/store"

    # TODO (MP) in private deployment, this needs to be dynamic (and not generated during bake time).
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

    file{"/etc/redis/redis-diskstore.conf":
        content => template(
            "redis/diskstore.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }

    # Upstart related things.
    service { "redis-diskstore":
        ensure  => running,
        provider => upstart,
        enable  => true,
        require => File["/etc/init.d/redis-diskstore"],
    }
    file{"/etc/init.d/redis-diskstore":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => File["/etc/init/redis-diskstore.conf"],
    }
    file{"/etc/init/redis-diskstore.conf":
        content => template(
            "redis/redis.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }
}

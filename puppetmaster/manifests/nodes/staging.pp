node "staging.aerofs.com" inherits default {

    # port listing:
    #   80   => verkehr
    #   443  => nginx (with proxy pass to 8080)
    #   8080 => tomcat (internal)
    #   8888 => zephyr
    #   9328 => ejabberd
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # we include the base servlet lib and assume people will deploy their own apps.
    include servlet

    include redis

    class{"verkehr":
        subscribe_port => 80
    }

    class { "ejabberd":
        mysql_password => hiera("mysql_password"),
    }

    class { "ejabberd::firewall_rules" :
        port => 9328,
    }

    include zephyr
}

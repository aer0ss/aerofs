node "privatecloud.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # Include license website
    include lizard
    # Include nginx configurations
    include lizard::nginx

    # Include mysql
    include mysql
    class {'mysql::server':
        config_hash => {
            'bind_address' => "127.0.0.1"
        },
    }
    # Remove extra users
    class {'mysql::server::account_security': }
    mysql::db {'lizard':
        user => 'lizard',
        password => '',
        host => 'localhost',
        grant => [all],
    }
}

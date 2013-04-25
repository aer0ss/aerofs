node "tts.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    } 

    $mysql_config = hiera("mysql_config")

    include nginx
    include synctime
    class{"synctime::config":
        mysql_config => $mysql_config
    }

    nginx::resource::vhost {"${fqdn}":
        listen_port          => '80',
        proxy                => 'http://127.0.0.1:8080',
        client_max_body_size => '4096m',
        ensure               => present,
    }
}

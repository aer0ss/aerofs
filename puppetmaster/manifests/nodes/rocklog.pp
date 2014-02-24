node "rocklog.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    # FIXME (AG): really, this should be triks.<domain>
    class { "rocklog" :
        triksdn => "triks.aerofs.com"
    }
}

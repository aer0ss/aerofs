node /^verkehr\.aerofs\.com$/ inherits default {


    class { "verkehr": }

    users::add_user {
        [ hiera('dev_users') ]:
    }
}

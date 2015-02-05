node /^verkehr\.aerofs\.com$/ inherits default {

    include verkehr
    include verkehr::ssl
    # firewall provides the firewall
    # verkehr::firewall_rules provides the firewall rule
    include firewall
    include verkehr::firewall_rules

    include public-deployment-secret

    users::add_user {
        [ hiera('dev_users') ]:
    }

    file { '/data':
        ensure => 'link',
        target => '/mnt',
    }
}

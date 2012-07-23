# == Class: users
#
# Full description of class users here.
#
# === Parameters
#
# Document parameters here.
#
# [*sample_parameter*]
#   Explanation of what this parameter affects and what it defaults to.
#   e.g. "Specify one or more upstream ntp servers as an array."
#
# === Variables
#
# Here you should define a list of variables that this module would require.
#
# [*sample_variable*]
#   Explanation of how this variable affects the funtion of this class and if it
#   has a default. e.g. "The parameter enc_ntp_servers must be set by the
#   External Node Classifier as a comma separated list of hostnames." (Note,
#   global variables should not be used in preference to class parameters  as of
#   Puppet 2.6.)
#
# === Examples
#
#  class { users:
#    servers => [ 'pool.ntp.org', 'ntp.local.company.com' ]
#  }
#
# === Authors
#
# Author Name <author@domain.com>
#
# === Copyright
#
# Copyright 2011 Your name here, unless otherwise noted.
#
class users {

    define add_user($userdata = undef, $groups = undef, $ssh_keys = undef)
    {
        if ($userdata == undef) { $user = hiera("${title}") }
        else { $user = $userdata }

        $username = $user[username]
        $realname = $user[realname]
        $shell = $user[shell]
        $home = $user[home]

        if ($ssh_keys == undef) { $ssh_keys_real = $user[ssh_keys] }
        else  { $ssh_keys_real = $ssh_keys }

        if ($groups == undef) { $groups_real = $user[groups] }
        else { $groups_real = $groups }

        if ($username == undef) { fail ("username cannot be undefined") }
        elsif ($realname == undef) { fail ("real name cannot be undefined") }
        elsif ($shell == undef)  { fail ("shell cannot be undefined") }
        elsif ($home == undef) { fail ("home cannot be undefined") }

        user { "${username}":
            ensure     => present,
            comment    => "${realname}",
            shell      => "${shell}",
            home       => "${home}",
            groups     => ["${groups_real}"],
            membership => inclusive, #make sure that the groups listed are the only groups the user is a member of
            managehome => true,
            notify     => Exec[ "passwd -d -e ${username}" ],

        }

        file {"${home}":
            ensure  => directory,
            owner   => $username,
            group   => $username,
            mode    => 750,
            source  => [ "puppet:///users/home/$name/",
                         "puppet:///users/skel/", ],
            require => User["${username}"],
        }

        file { "${home}/.ssh":
            ensure  => directory,
            owner   => $username,
            group   => $username,
            mode    => 700,
            require => File["${home}"],
        }

        file { "${home}/.ssh/authorized_keys":
            ensure  => file,
            owner   => $username,
            group   => $username,
            mode    => 700,
            require => File["${home}/.ssh"],
            content => template("users/authorized_keys.erb"),
        }

        #expire the user's password, forcing them to set a new one on each account
        exec { "passwd -d -e ${username}":
            refreshonly => true,
            path        => [ "/sbin", "/bin", "/usr/bin", "/usr/sbin" ],
        }

    }
}

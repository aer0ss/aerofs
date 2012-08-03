# == Class: line
#
# Manage whether or not a line exists in a file
#
# === Parameters
#
# [*ensure*]
#   only present is supported
# [*line*]
#   the line in question
# [*file*]
#   the file that should contain the line
#
# === Variables
#
# === Examples
#
#    line{ "root bashrc cd aerofs repo":
#        ensure => present,
#        file => "/root/.bashrc",
#        line => "alias cdar='cd ~/repos/aerofs'",
#    }
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
define line($file, $line, $ensure = 'present') {
    case $ensure {
        default : { err ( "unknown ensure value ${ensure}" ) }
        present: {
            exec { "/bin/echo '${line}' >> '${file}'":
                unless => "/bin/grep -qFx '${line}' '${file}'"
            }
        }
        # TODO: (PH) add case for absent
    }
}

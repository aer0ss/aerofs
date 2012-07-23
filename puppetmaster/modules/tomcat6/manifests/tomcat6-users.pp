define tomcat6::add_user(
    $username = $title,
    $password,
    $roles,
    $id 
) {

    if ($id !~ /^\d\d\d/) {
        fail("the id '${id}' does not match the expected id formt of '###'")
    } elsif ($id == '000' or $id == '999') { 
        fail("the id cannot be 000 or 999")
    }

    $tempDir = "/tmp"

    file { "${tempDir}/tomcat6.d":
        ensure  => directory,
    }

    file { "${tempDir}/tomcat6.d/000-tomcat6-users.xml":
        ensure  => file,
        content => template('tomcat6/tomcat-users_header.erb'),
        notify  => Exec['update-tomcat6-users.xml'],
    }

    file { "${tempDir}/tomcat6.d/${id}-${name}":
        ensure  => file,
        content => template('tomcat6/tomcat-users_body.erb'),
        notify  => Exec['update-tomcat6-users.xml'],
    }

    file { "${tempDir}/tomcat6.d/999-tomcat6-users.xml":
        ensure  => file,
        content => template('tomcat6/tomcat-users_footer.erb'),
        notify  => Exec['update-tomcat6-users.xml'],
    }

    exec { 'update-tomcat6-users.xml':
        command     => "/bin/cat ${tempDir}/tomcat6.d/* > /etc/tomcat6/tomcat-users.xml; rm -f ${tempDir}/tomcat6.d/*",
        refreshonly => true,
        subscribe   => File["$tempDir/tomcat6.d"],
        notify      => Service['tomcat6'],
        require     => Package["tomcat6"],
    }
}

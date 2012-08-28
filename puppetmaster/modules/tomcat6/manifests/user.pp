# We only support one user at the moment.  If multiple users are required,
# we can revisit this.
define tomcat6::user(
    $username = $title,
    $password,
    $roles,
    $id
) {
    file { "/etc/tomcat6/tomcat-users.xml":
        ensure  => present,
        content => template("tomcat6/tomcat-users.xml.erb"),
        notify      => Service['tomcat6'],
        require     => Package["tomcat6"]
    }
}

class servlet::base(
        $tomcat6_user = {"roles" => "manager", "password" => "password"},
 ) {
    include tomcat6

    tomcat6::user {"manager":
        password => $tomcat6_user[password],
        roles    => $tomcat6_user[roles],
        id       => "001",
    }
}

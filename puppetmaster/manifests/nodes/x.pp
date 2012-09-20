node /^x\.aerofs\.com$/ inherits default {
  class { "ejabberd":
    mysql_password => hiera("mysql_password")
  }

  users::add_user {
    [ hiera('dev_users') ]:
  }
} 

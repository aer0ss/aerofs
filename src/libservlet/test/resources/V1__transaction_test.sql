-- This schema just sets up a simple table and is meant for testing the transaction system

CREATE TABLE `users` (
  `u_id` VARCHAR(30) NOT NULL,
  PRIMARY KEY (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

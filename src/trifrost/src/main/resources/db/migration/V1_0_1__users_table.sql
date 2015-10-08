--
-- Table structure for table `users`
--
CREATE TABLE `users` (
    `user_id` char(32) NOT NULL,
    PRIMARY KEY(`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO users SELECT DISTINCT (user_id) FROM addresses;

ALTER TABLE addresses
    ADD FOREIGN KEY (`user_id`) REFERENCES `users` (user_id) ON DELETE CASCADE;

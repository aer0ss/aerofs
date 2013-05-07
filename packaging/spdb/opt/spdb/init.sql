drop procedure if exists createUser;
delimiter $$
create procedure createUser(password varchar(50))
begin
IF (SELECT EXISTS(SELECT 1 FROM `mysql`.`user` WHERE `user` = 'aerofs_sp')) = 0 THEN
    begin
    set @sql = CONCAT("GRANT ALL ON aerofs_sp.* to aerofs_sp IDENTIFIED BY '", password, "'");
    prepare stmt from @sql;
    execute stmt;
    deallocate prepare stmt;
    end;
END IF;
end $$
delimiter ;

-- Create databases.
CREATE DATABASE IF NOT EXISTS aerofs_sp;

-- "Create user if not exists" and grant priviledges.
call createUser('password');

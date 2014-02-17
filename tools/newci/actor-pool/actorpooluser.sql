grant usage on *.* to 'actorpoolservice'@'localhost' identified by 'temp123';
grant all privileges on actorpool.* to 'actorpoolservice'@'localhost';
flush privileges;

<?php

$common = file_get_contents('/opt/config/common.properties');
$server = file_get_contents('/opt/config/server.properties');

echo $common;
echo $server;

?>

<?php

$common = file_get_contents('/opt/config/properties/common.properties');
$server = file_get_contents('/opt/config/properties/server.properties');

echo $common;
echo $server;

?>

<?php

$common = file_get_contents('/opt/config/common.properties');
$client = file_get_contents('/opt/config/client.properties');

echo $common;
echo $client;

?>

<?php

$common = file_get_contents('/opt/config/properties/common.properties');
$client = file_get_contents('/opt/config/properties/client.properties');

echo $common;
echo $client;

?>

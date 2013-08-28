<?php

if ($_SERVER['REQUEST_METHOD'] === 'POST')
{
    if (isset($_POST["context"]) && isset($_POST["key"]) && isset($_POST["value"]))
    {
        $context = $_POST["context"];
        $key = $_POST["key"];
        $value = $_POST["value"];

        // Sed in the new value. Only works if the key already existed in this
        // context.
        $cmd = "sed -i 's/" . $key . "=.*/" . $key . "=" . $value . "/g' /opt/config/properties/" . $context . ".properties";
        echo exec($cmd, $out, $rv);
    }
    else
    {
        header('Not all parameters have been set', true, 404);
    }
}
else
{
    $common = file_get_contents('/opt/config/properties/common.properties');
    $server = file_get_contents('/opt/config/properties/server.properties');

    echo $common;
    echo $server;
}

?>

<?php

if (isset($_FILES["package"]["tmp_name"]) &&
    $_FILES["package"]["tmp_name"]) {
    copy($_FILES["package"]["tmp_name"], "/mnt/share/.config/cert.pem");
    chmod("/mnt/share/.config/cert.pem", 777);
    echo "<html>
        <head>
            <title>AeroFS Virtual Machine Host</title>
        </head>
        <body>
            <p>Thank you for uploading your software package. You can close this window now.</p>
        </body>
        </html>";
}
else {
    echo "<meta HTTP-EQUIV='REFRESH' content='0; url=/'>";
}

?>

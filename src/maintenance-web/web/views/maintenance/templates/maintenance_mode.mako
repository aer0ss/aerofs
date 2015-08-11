<%inherit file="marketing_layout.mako"/>
<%! page_title = "Maintenance Mode" %>

<div class="col-sm-12 text-center">
    <h3>Down for maintenance</h3>
    <p>Your AeroFS adminstrators are performing maintenance.</p>
    <p><font size="-4">Administrators please visit your
    <a id="mng-link" href="">Appliance Management Interface</a> to configure
    this system.</font></p>
</div>

<script type="text/javascript">
    $(window).load(function()
    {
        var bunker = "http://" + window.location.hostname + ":8484";

        var mngLink = document.getElementById("mng-link");
        if (mngLink != null) {
            mngLink.setAttribute("href", bunker);
        }
    })
</script>

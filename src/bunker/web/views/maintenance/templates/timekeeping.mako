<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Timekeeping" %>

<%namespace name="csrf" file="csrf.mako"/>

<div class="page-block">
    <h2>Timekeeping</h2>
    <h4>NTP server</h4>

    <p>The AeroFS Appliance requires accurate timekeeping for two-factor
    authentication.</p>
    <p>By default, it will pull time from the Ubuntu NTP pool.  If you leave
    this field blank, it will continue to do so.  If you need to use an
    internal time server, enter the hostname of that server below.</p>

    <form method="POST" class="form-horizontal" role="form">
        ${csrf.token_input()}
        <div class="form-group">
            <div class="col-sm-6">
                <label for="ntp-server">NTP Server:</label>
                <input class="form-control" id="ntp-server" name="ntp-server" type="text" value="${server}" />
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</div>

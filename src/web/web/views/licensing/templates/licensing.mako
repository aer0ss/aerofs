<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Licensing" %>

<h2>Licensing</h2>

<%
    mng_url = 'https://' + str(request.registry.settings['base.host.unified']) + '/admin/setup'
%>

<p>
    Information regarding your license quota is available below. If you need to update your
    license, you may do so by obtaining a new license file from your
    <a href="https://pc.aerofs.com/" target="_blank">Private Cloud Dashboard</a> and subsequently
    uploading it to your <a href="${mng_url}" target="_blank">Management Interface</a>.
</p>

<br/>

<div class="row">
    <div class="col-sm-12">
        <div class="my-table">
            <div class="my-table-body">
                <div class="row" style="padding-bottom: 10px;">
                    <div class="col-sm-2 col-md-4">
                        Seats available
                    </div>
                    <div class="col-sm-4 col-md-6">
                        ${license_seats}
                    </div>
                </div>
                <div class="row" style="padding-bottom: 10px;">
                    <div class="col-sm-2 col-md-4">
                        Seats used
                    </div>
                    <div class="col-sm-4 col-md-6">
                        ${license_seats_used}
                    </div>
                </div>
                %if external_user_count > 0:
                    <div class="row" style="padding-bottom: 10px;">
                        <div class="col-sm-2 col-md-4">
                            External restricted users
                        </div>
                        <div class="col-sm-4 col-md-6">
                            ${external_user_count}
                        </div>
                    </div>
                %endif
                <div class="row" style="padding-bottom: 10px;">
                    <div class="col-sm-2 col-md-4">
                        <b>Seats remaining</b>
                    </div>
                    <div class="col-sm-4 col-md-6">
                        <b>${license_seats - license_seats_used}</b>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<hr/>
<p>
    If you have any questions about licensing please contact the AeroFS sales team at
    <a href="mailto:sales@aerofs.com">sales@aerofs.com</a>.
</p>
<%inherit file="maintenance_layout.mako"/>
<%! page_title = "On-Site Servers" %>

<%namespace name="csrf" file="csrf.mako"/>

<h2>On-Site Log Server</h2>

<p>Sometimes sending logs directly to AeroFS isn't an option. The AeroFS On-Site Log Server allows admins to easily
    deploy a log collection server on-site, and send AeroFS client logs to that server instead.</p>

<p>To configure the log server, please consult our
    <a href="https://support.aerofs.com/hc/en-us/articles/204862080-How-Do-I-Deploy-An-On-Site-Log-Collection-Server-"
       target="_blank">support documentation</a>.
</p>

<h3>Debian Download</h3>

<ul>
    <li><a href="/static/aerofs-dryad.deb">AeroFS On-Site Log Server</a></li>
</ul>

<hr/>

<div class="page-block">
    <h2>Configurable Docker Registry</h2>

    <p>You can point your appliance to pull new AeroFS images from any one of the authorized AeroFS docker registries. During upgrades, your appliance will
    pull images from the registry you enter below.</>

    <br>

    <p>You also have the option to deploy and configure your own docker registry. This registry will mirror the AeroFS docker registry
        and download new images from it weekly. You can then use your own private registry to upgrade your appliance.</p>

    <form method="POST" class="form-horizontal" role="form">
        ${csrf.token_input()}
        <div class="form-group">
            <div class="col-sm-6">
                <label for="prv-registry">Hostname/IP of your registry:</label>
                <input class="form-control" id="prv-registry" name="prv-registry" type="text" value="${server}" />
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</div>

<div class="page-block">
    <h4>What is Team Server?</h4>
    <p>
        AeroFS Team Server is an software application that securely backs
        up your entire organization's files at a central location, either on your local drive or
        Amazon S3. It provides a central access point for your organization's data.
        ## TOOD (WW) this should points to the public Web site rather than a marketing page
        ## inside the private deployment. Add a static path that points to the public Web
        ## site.
        <a href="https://www.aerofs.com/team-server/">Learn more about Team Server</a>.
    </p>
</div>
<div class="page-block">
    <h4>How to install Team Server?</h4>
    <p>
        It is as easy as installing AeroFS client applications:
    </p>
    <ol>
        <li>Download and run the installer.</li>
        <li>Enter your AeroFS credentials.</li>
        <li>Done!</li>
    </ol>
    <p>
        If you're still unsure, our knowledge base has <a href="https://support.aerofs.com/entries/23596201">detailed setup instructions</a>.
    </p>
</div>
<div class="page-block">
    <h4>How to add users to sync?</h4>
    <p>
        Once installed, the server will immediately sync with your own AeroFS clients.
        To sync with colleagues and clients, simply
        <a href="${request.route_path('org_users')}">invite them to your organization</a>.
        After they accept the invitation, all their AeroFS files will be synced
        to the Team Server.
    </p>
</div>
<div class="page-block">
    <h4>What if I install multiple Team Servers?</h4>
    <p>
        Multiple servers installed under the same account will automatically
        replicate data amongst each other, whether they are in an office LAN or over
        the Internet.
    </p>
</div>
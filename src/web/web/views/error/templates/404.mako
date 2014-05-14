<%inherit file="marketing_layout.mako"/>
<%! page_title = "Page Not Found" %>

<%block name="scripts">
    <script src="${request.static_path('web:static/js/compiled/errors.js')}"></script>
</%block>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        <h2>404: Page Not Found</h2>
        <p>Sorry, the page or file you are looking for cannot be found here.</p>
        <div id="no-referrer" class="extra-error-info">
            <p>You may not have been able to find the page you were after because of:</p>
            <ol>
                <li>A mis-typed address</li>
                <li>An out-of-date bookmark or favorite</li>
                <li>A search engine that has an out-of-date listing for us</li>
            </ol>
        </div>
        <div id="search-referrer" class="extra-error-info">
            <p>It looks like you found this page via a search on <a id="search-engine-link" target='_blank'></a>. The search engine's index appears to be out of date.</p>
        </div>
        <div id="our-fault" class="extra-error-info">
            <p>It looks like this 404 error is our fault! If you want, you could contact <a href="mailto:support@aerofs.com">support@aerofs.com</a> and let them know how you reached this page.</p>
            <p>Please accept our apologies and be assured that the AeroFS developer responsible for this broken link will be gently chided until she or he fixes the problem.</p>
        </div>
        <div id="other-sites-fault" class="extra-error-info">
            <p>It looks like you were sent to this page by another site whose link is out of date.</p>
        </div>
        <p><a href="/">&laquo; Return to the AeroFS home page</a></p>
    </div>
</div>

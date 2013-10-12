<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<p>Welcome to the AeroFS Site Configuration Interface. This page will guide you through setting up your private AeroFS installation.</p>
<p>You can start over at any time. Changes will not be visible until they are applied during the final stage.</p>
<hr/>

<form method="get">
    <input type="hidden" name="page" value="1"/>
    <button id="submitButton" class="btn btn-primary" type="submit">Let's get started!</button>
</form>

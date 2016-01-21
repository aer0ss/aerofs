<dl class="dl-horizontal">
    <dt>Licensed to:</dt>
    <dd>${render_license_field('license_company')}</dd>
    <dt>Type:</dt>
    <dd>${render_license_field('license_type')}</dd>
    <dt>Valid until:</dt>
    <dd>
        ${render_license_field('license_valid_until')}
        ## Because the license must be present before this page can be rendered, the license must
        ## be invalid if the condition is true.
        %if not is_license_present_and_valid:
            <span class="badge badge-important" style="margin-left: 5px;">Expired</span>
        %endif
    </dd>
    <dt>Allowed seats:</dt>
    <dd>${render_license_field('license_seats')}</dd>
</dl>

<%def name='render_license_field(key)'>
    %if key in current_config:
        ${current_config[key].capitalize()}
    %else:
        -
    %endif
</%def>
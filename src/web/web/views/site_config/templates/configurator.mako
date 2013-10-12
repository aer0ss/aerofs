<h3>Site Configuration</h3>

<%
    page = request.params.get('page')
    if not page:
        page = 0
    else:
        page = int(page)
%>

%if page == 0:
    <%namespace name="welcome_page" file="configurator/welcome_page.mako"/>
    <%welcome_page:body/>
%elif page == 1:
    <%namespace name="hostname_page" file="configurator/hostname_page.mako"/>
    <%hostname_page:body/>
%elif page == 2:
    <%namespace name="email_page" file="configurator/email_page.mako"/>
    <%email_page:body/>
%elif page == 3:
    <%namespace name="cert_page" file="configurator/cert_page.mako"/>
    <%cert_page:body/>
%elif page == 4:
    <%namespace name="apply_page" file="configurator/apply_page.mako"/>
    <%apply_page:body/>
%endif

## TODO (MP) need a page to facilitate initial user setup (can't expect them to
## navigate to the marketing page).

<%namespace name="common" file="configurator/common.mako"/>
${common.scripts(page)}

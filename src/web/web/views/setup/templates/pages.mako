<%
    page = request.params.get('page')
    if not page:
        page = 0
    else:
        page = int(page)
%>

%if page == 0:
    <h3>Welcome</h3>
    <%namespace name="welcome_page" file="pages/welcome_page.mako"/>
    <%welcome_page:body/>
%elif page == 1:
    <h3>Step 1 of 4</h3>
    <%namespace name="hostname_page" file="pages/hostname_page.mako"/>
    <%hostname_page:body/>
%elif page == 2:
    <h3>Step 2 of 4</h3>
    <%namespace name="email_page" file="pages/email_page.mako"/>
    <%email_page:body/>
%elif page == 3:
    <h3>Step 3 of 4</h3>
    <%namespace name="cert_page" file="pages/cert_page.mako"/>
    <%cert_page:body/>
%elif page == 4:
    <h3>Step 4 of 4</h3>
    <%namespace name="apply_page" file="pages/apply_and_create_user_page.mako"/>
    <%apply_page:body/>
%endif

## TODO (MP) need a page to facilitate initial user setup (can't expect them to
## navigate to the marketing page).

<%namespace name="common" file="pages/common.mako"/>
${common.scripts(page)}

<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<h2>Billing History</h2>
<table class="table">
    <thead>
        <tr>
            <th>Post Date</th>
            <th>Billing Cycle</th>
            <th>Through</th>
            <th>Amount</th>
        </tr>
    </thead>
    <tbody>
% for invoice in invoices:
    <%
        ## id = invoice['id']
        date = invoice['date']
        period_start = invoice['period_start']
        period_end = invoice['period_end']
        total = invoice['total']
        ## paid = invoice['paid']

        ## Format total. TODO (WW) make it a utility function?
        total = str(total / 100) + '.' + str(format(total % 100)).zfill(2)
    %>
        <tr>
            <td class="format-time">${date}</td>
            <td class="format-time">${period_start}</td>
            <td class="format-time">${period_end}</td>
            <td class="format-currency">$${total}</td>
        </tr>
% endfor
    </tbody>
</table>

<%block name="scripts">
    <script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.0/jquery-ui.min.js"></script>
    <script type="text/javascript">
        $(document).ready(
            function() {
                $('.format-time').text(function(i, v) {
                    return $.datepicker.formatDate('D, d M yy', new Date(v * 1000));
                });
            });
    </script>
</%block>
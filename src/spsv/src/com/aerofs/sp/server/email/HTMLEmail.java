package com.aerofs.sp.server.email;

import com.aerofs.lib.S;
import com.aerofs.sp.common.SubscriptionParams;
import com.aerofs.sp.server.lib.SPParam;

import javax.annotation.Nullable;
import java.io.IOException;

public class HTMLEmail implements IEmail {

    private final static String HEADER =

        "<!doctype html><html>" +
        "<body>" +
        "<table cellpadding=\"0\" cellspacing=\"0\" width=\"650\" align=\"center\" style=\"font-family:arial,sans-serif;font-size:13px;\">" +
        "   <tr>" +
        "       <td>" +
        "           <table cellpadding=\"0\" cellspacing=\"0\">" +
        "               <tr>" +
        "                   <td style=\"padding:10px 0\">" +
        "                       <a href=\"\"><img src=\"" + SPParam.STATIC_ASSETS + "/img/logo.png\"></a>" +
        "                   </td>" +
        "               </tr>" +
        "           </table>" +
        "       </td>" +
        "   <tr>" +
        "       <td style=\"border:solid #E8E8E8;border-width:1px 0;padding:50px 70px;color:#535353;\">"+
        "           <table cellpadding=\"0\" cellspacing=\"0\">";



    private final String _footer;

    public static final String H1STYLE = "margin:0;font-size:25px;font-family:arial, sans-serif;color:#17466B;";
    public static final String PSTYLE  = "margin:0;";

    private final  StringBuilder _sb = new StringBuilder(HEADER);
    private int _sectionCount = 0;
    private boolean _finalized = false;

    public HTMLEmail(final boolean unsubscribe, @Nullable final String unsubscribeId)
    {
        final String unsubscribeText = unsubscribe ? "<a style=\"color:#999999;\" href=\"" +
                                        SubscriptionParams.UNSUBSCRIPTION_URL +
                                        unsubscribeId + "\">Unsubscribe</a>" :
                                    "<p style\"color:#999999;\">This is a one time email</p>";
        _footer =
                " </table>" +
        "       </td>" +
        "   </tr>" +
        "   <tr> "+
        "   <td style=\"padding:10px 0;\">"+
        "   <table cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:11px;color:#999999;width:100%;\">" +
        "       <tr>"+
        "            <td align=\"left\">" +
        "                Copyrights &copy; " + S.COPYRIGHT +
        "            </td>" +
        "            <td align=\"right\">" +
        "                <table align=\"right\">" +
        "                    <tr>" +
        "                        <td><a style=\"color:#999999;\" href=\"http://www.aerofs.com/\">Home</a></td>" +
        "                        <td width=\"10\"/>" +
        "                        <td><a style=\"color:#999999;\" href=\"http://blog.aerofs.com/\">Blog</a></td>" +
        "                        <td width=\"10\"/>" +
        "                        <td><a style=\"color:#999999;\" href=\"http://www.twitter.com/aerofs\">Twitter</a></td>" +
        "                        <td width=\"10\"/>" +
        "                        <td><a style=\"color:#999999;\" href=\"http://support.aerofs.com/\">Support</a></td>" +
        "                        <td width=\"10\"/>" +
        "                        <td>" + unsubscribeText + "</td>" +
        "                    </tr>" +
        "                </table>" +
        "            </td>" +
        "        </tr>" +
        "   </table>" +
        "</td>" +
        "</tr>" +
        "</table>" +
        "</body>" +
        "</html>";
    }

    @Override
    public void addSection(final String header, final String body)
            throws IOException
    {
        if (_finalized) throw new IOException("cannot add section to a finalized email");

        String b = body.replace("\n", "<br/>");
        if (_sectionCount > 0) {
            //spacing
            _sb.append("<tr><td height=\"20\" /></tr>");
        }

        _sb.append("<tr><td>");

        _sb.append("<h1 style=\"" + H1STYLE + "\">" + header + "</h1>");

        _sb.append("</td></tr>");

        //spacing
        _sb.append("<tr><td height=\"3\" /></tr>");

        _sb.append("<tr><td>");
        _sb.append("<p style=\"" + PSTYLE + "\">" + b + "</p>");
        _sb.append("</td></tr>");

        _sectionCount++;
    }

    @Override
    public void addSignature(final String valediction, final String name, final String ps)
            throws IOException
    {

        if (_finalized) throw new IOException("cannot add signature to a finalized email");

        final String signature =
            "               <tr>" +
            "                   <td height=\"30\"/>" +
            "               </tr>" +
            "               <tr>" +
            "                   <td style=\"font-style:italic;font-size:12px\">" + valediction + "</td>" +
            "               </tr>" +
            "               <tr>" +
            "                   <td height=\"3\"/>" +
            "               </tr>" +
            "               <tr>" +
            "                   <td style=\"font-style:italic;font-size:12px;\">" + name + "</td>" +
            "               </tr>" +
            "               <tr>" +
            "                   <td height=\"20\"/>" +
            "               </tr>" +
            "              <tr>" +
            "                   <td style=\"font-style:italic;font-size:12px;font-weight:bold\">"+ ps + "</td>" +
            "               </tr>";

        _sb.append(signature);
    }

    public String getEmail()
    {

        if (!_finalized) {
            _finalized = true;
            _sb.append(_footer);
        }

        return _sb.toString();
    }
}

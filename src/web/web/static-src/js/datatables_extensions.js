/**
 * Datatables helper functions
 */

function forceLogout() {
    var sessionCookieName = "session"; // Delete session reference to send user back to login page
    document.cookie = sessionCookieName + '=; expires=Thu, 01 Jan 1970 00:00:01 GMT; path=/';
    document.location.reload(true);
}

/**
 * This method should be used for the 'fnServerData' parameter when initializing a dataTable
 * in javascript. It handles AJAX errors returned by our web server and returns them correctly
 * to the user, and can be extended in the future for more specialized error handling.
 */
function dataTableAJAXCallback(sUrl, aoData, fnCallback, oSettings) {
    oSettings.jqXHR = $.ajax({
        "url": sUrl,
        "data": aoData,
        "success": function(json) {
            // TODO remove this block
            if (json.error) {
                // permission issue, force logout
                if (json.error.search("not authenticated") >= 0) {
                    forceLogout();
                } else {
                    showErrorMessage(json.error);
                }
            }
            $(oSettings.oInstance).trigger('xhr', oSettings);
            fnCallback(json);
        },
        "dataType": "json",
        "cache": false,
        "type": oSettings.sServerMethod,
        "error": showErrorMessageFromResponse
    });
}

/**
 * Datatables bootstrap paging extension
 */

/* Default class modification */
$.extend( $.fn.dataTableExt.oStdClasses, {
    "sWrapper": "dataTables_wrapper form-inline"
} );

/* API method to get paging information */
$.fn.dataTableExt.oApi.fnPagingInfo = function ( oSettings )
{
    return {
        "iStart":         oSettings._iDisplayStart,
        "iEnd":           oSettings.fnDisplayEnd(),
        "iLength":        oSettings._iDisplayLength,
        "iTotal":         oSettings.fnRecordsTotal(),
        "iFilteredTotal": oSettings.fnRecordsDisplay(),
        "iPage":          Math.ceil( oSettings._iDisplayStart / oSettings._iDisplayLength ),
        "iTotalPages":    Math.ceil( oSettings.fnRecordsDisplay() / oSettings._iDisplayLength )
    };
}

/* Bootstrap style pagination control */
$.extend( $.fn.dataTableExt.oPagination, {
    "bootstrap": {
        "fnInit": function(oSettings, nPaging, fnDraw) {
            var oLang = oSettings.oLanguage.oPaginate;
            var fnClickHandler = function (e) {
                e.preventDefault();
                if (oSettings.oApi._fnPageChange(oSettings, e.data.action)) {
                    fnDraw( oSettings );
                }
            };

            $(nPaging).addClass('pagination pagination-small').append(
                '<ul>'+
                    '<li class="prev disabled"><a href="#">&larr;</a></li>'+
                    '<li class="next disabled"><a href="#">&rarr; </a></li>'+
                    '</ul>'
            );
            var els = $('a', nPaging);
            $(els[0]).bind( 'click.DT', { action: "previous" }, fnClickHandler );
            $(els[1]).bind( 'click.DT', { action: "next" }, fnClickHandler );
        },

        "fnUpdate": function (oSettings, fnDraw) {
            var iListLength = 5;
            var oPaging = oSettings.oInstance.fnPagingInfo();
            var an = oSettings.aanFeatures.p;
            var i, j, sClass, iStart, iEnd, iHalf=Math.floor(iListLength/2);

            // Don't show page numbers for single-page data
            if (oPaging.iTotalPages <= 1) return;

            if (oPaging.iTotalPages < iListLength) {
                iStart = 1;
                iEnd = oPaging.iTotalPages;
            }
            else if (oPaging.iPage <= iHalf) {
                iStart = 1;
                iEnd = iListLength;
            } else if (oPaging.iPage >= (oPaging.iTotalPages-iHalf)) {
                iStart = oPaging.iTotalPages - iListLength + 1;
                iEnd = oPaging.iTotalPages;
            } else {
                iStart = oPaging.iPage - iHalf + 1;
                iEnd = iStart + iListLength - 1;
            }

            for (i=0, iLen=an.length; i<iLen; i++) {
                // Remove the middle elements
                $('li:gt(0)', an[i]).filter(':not(:last)').remove();

                // Add the new list items and their event handlers
                for (j=iStart; j<=iEnd; j++) {
                    sClass = (j==oPaging.iPage+1) ? 'class="active"' : '';
                    $('<li '+sClass+'><a href="#">'+j+'</a></li>')
                        .insertBefore( $('li:last', an[i])[0] )
                        .bind('click', function (e) {
                            e.preventDefault();
                            oSettings._iDisplayStart = (parseInt($('a', this).text(),10)-1) * oPaging.iLength;
                            fnDraw( oSettings );
                        } );
                }

                // Add / remove disabled classes from the static elements
                if (oPaging.iPage === 0) {
                    $('li:first', an[i]).addClass('disabled');
                } else {
                    $('li:first', an[i]).removeClass('disabled');
                }

                if (oPaging.iPage === oPaging.iTotalPages-1 || oPaging.iTotalPages === 0) {
                    $('li:last', an[i]).addClass('disabled');
                } else {
                    $('li:last', an[i]).removeClass('disabled');
                }
            }
        }
    }
} );

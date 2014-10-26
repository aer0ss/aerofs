$(document).ready(function(){
    $('.extra-error-info').hide();
    var strReferrer = document.referrer.toLowerCase();
    var searchReferral = ((strReferrer.indexOf(".looksmart.co")>0)||
                (strReferrer.indexOf(".ifind.freeserve")>0)||
                (strReferrer.indexOf(".ask.co")>0)||
                (strReferrer.indexOf("google.co")>0)||
                (strReferrer.indexOf("altavista.co")>0)||
                (strReferrer.indexOf("msn.co")>0)||
                (strReferrer.indexOf("yahoo.co")>0)
                );
    var ownReferral = ((strReferrer.indexOf("share.syncfs.com")>=0) ||
        (strReferrer.indexOf("aerofs.com")) >=0);
    if (strReferrer.length === 0) {
        $('#no-referrer').show();
    } else if (ownReferral) {
        $('#our-fault').show();
    } else if (searchReferral) {
        console.log(strReferrer);
        var problematicSearchEngine = strReferrer.split("/")[2];
        $('#search-engine-link').attr('href', "http://" + problematicSearchEngine).text(problematicSearchEngine);
        $('#search-referrer').show();
    } else {
        $('#other-sites-fault').show();
    }
});

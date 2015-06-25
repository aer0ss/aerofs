package com.aerofs.polaris.external_api.metadata;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.Identifiers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;

public class RestObjectResolver
{
    /*
    * A rest object sent from the web is agnostic of its place in the AeroFS dir hierarchy. This
    * This function attempts to add some context to the object sent from the web and create
    * well formed Rest Objects with valid sid and oid.
    *
    * The user's AeroFS root when sent from the web has sid = null and oid = OID.ROOT. We transform
    * that into the user's root SID and OID.ROOT.
    *
    * A shared folder sent from the web has sid = Shared folder SID and oid = OID.ROOT. We
    * transform that into user's root SID and SharedFolder SID
    *
    * Any other object has a well formed SID and OID, hence the object is just returned.
    */
    static UniqueID fromRestObject(AeroOAuthPrincipal principal, RestObject object)
    {
        SID rootSID = SID.rootSID(principal.getUser());
        if (object.getOID().equals(OID.ROOT)) {
            if (object.isRoot() || Identifiers.isRootStore(object.getSID())) {
                // User's root
                return rootSID;
            } else {
                // Shared Folder case. OID of an object sent from web can be OID.ROOT only if the object
                // is the user's root or a shared folder.
                return SID.storeSID2anchorOID(object.getSID());
            }
        } else {
            return object.getOID();
        }
    }

    static String toRestObject(SID sid, UniqueID oid)
    {
        if (Identifiers.isRootStore(oid) || sid.equals(oid)) {
            oid = OID.ROOT;
        }
        return new RestObject(sid, new OID(oid)).toStringFormal();
    }
}

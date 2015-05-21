package com.aerofs.daemon.transport.xmpp.presence;

import org.jivesoftware.smackx.packet.VCard;

import javax.annotation.Nullable;

/**
 * We are storing metadata for each device (=XMPP user) on his vCard.
 * This uses the XEP0054: http://xmpp.org/extensions/xep-0054.html
 * We are storing our data inside the DESC field.
 */
public class XMPPvCard extends VCard
{
    // Name of the field used to store the metadata
    protected static final String FIELD_NAME = "DESC";

    /**
     * Set the payload inside the vCard
     * @param data the payload to store
     */
    public void setMetadata(String data)
    {
        this.setField(FIELD_NAME, data, false);
    }

    /**
     * Retrieve the payload that may have been stored inside the vCard
     * @return the payload
     */
    public @Nullable String readMetadata()
    {
        return this.getField(FIELD_NAME);
    }
}

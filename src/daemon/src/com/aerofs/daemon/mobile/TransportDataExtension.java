/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.lib.Base64;
import com.aerofs.proto.Transport;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.xmlpull.v1.XmlPullParser;

class TransportDataExtension implements PacketExtension
{
    public static final String NAMESPACE = "http://aerofs.com/protocol/mobile";
    public static final String TAG = "transport";

    private Transport.PBTPHeader _proto;

    @Override
    public String getElementName()
    {
        return TAG;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    public Transport.PBTPHeader getProto()
    {
        return _proto;
    }

    public void setProto(Transport.PBTPHeader proto)
    {
        _proto = proto;
    }

    @Override
    public String toXML()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(TAG);
        sb.append(" xmlns=\"").append(NAMESPACE).append('"');
        sb.append('>');
        if (_proto != null) sb.append(Base64.encodeBytes(_proto.toByteArray()));
        sb.append("</").append(TAG).append('>');
        return sb.toString();
    }

    public static TransportDataExtension getExtension(Packet packet)
    {
        return (TransportDataExtension)packet.getExtension(TAG, NAMESPACE);
    }

    static class TransportDataIQ extends IQ
    {
        private final TransportDataExtension _data;

        public TransportDataIQ(TransportDataExtension data)
        {
            _data = data;
        }

        public TransportDataExtension getData()
        {
            return _data;
        }

        @Override
        public String getChildElementXML()
        {
            return (_data == null) ? "" : _data.toXML();
        }
    }


    private static class TransportDataProvider implements PacketExtensionProvider, IQProvider
    {
        static final TransportDataProvider _instance = new TransportDataProvider();

        private TransportDataProvider() {}

        @Override
        public TransportDataExtension parseExtension(XmlPullParser parser) throws Exception
        {
            TransportDataExtension data = new TransportDataExtension();
            Transport.PBTPHeader proto = Transport.PBTPHeader
                    .parseFrom(Base64.decode(parser.nextText()));
            data.setProto(proto);
            return data;
        }

        @Override
        public IQ parseIQ(XmlPullParser parser) throws Exception
        {
            return new TransportDataIQ(parseExtension(parser));
        }
    }

    static {
        ProviderManager pm = ProviderManager.getInstance();
        pm.addIQProvider(TAG, NAMESPACE, TransportDataProvider._instance);
        pm.addExtensionProvider(TAG, NAMESPACE, TransportDataProvider._instance);
    }

    public static void init()
    {
        // just ensure static initializers have run
    }
}

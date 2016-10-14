package com.aerofs.sp.server.saml;

import com.aerofs.base.Loggers;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallingException;
import org.slf4j.Logger;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;

public class SAMLUtil
{
    private static final Logger l = Loggers.getLogger(SAMLUtil.class);

    // Boilerplate to build SAML objects.
    @SuppressWarnings("unchecked")
    static <T> T buildSAMLObject(final Class<T> clazz)
            throws NoSuchFieldException, IllegalAccessException
    {
        XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory();
        QName defaultElementName = (QName) clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
        T object = (T) builderFactory.getBuilder(defaultElementName)
                .buildObject(defaultElementName);
        return object;
    }

    static void logSAMLObject(final XMLObject object, String objType)
            throws IllegalAccessException, TransformerException, IOException,
            MarshallingException, ParserConfigurationException, NoSuchFieldException
    {
        try {
            DocumentBuilder builder;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            builder = factory.newDocumentBuilder();

            Document document = builder.newDocument();
            Marshaller out = Configuration.getMarshallerFactory().getMarshaller(object);
            out.marshall(object, document);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
            String xmlString = result.getWriter().toString();
            l.debug("{}: {}",objType, xmlString);
        } catch (ParserConfigurationException | MarshallingException | TransformerException e) {
            l.error("failed to log  saml object {}", e);
        }
    }
}

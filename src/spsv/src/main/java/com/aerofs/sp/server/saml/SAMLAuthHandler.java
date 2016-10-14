package com.aerofs.sp.server.saml;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.SAML;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.sp.server.IExternalAuthHandler;
import com.aerofs.sp.server.IdentitySessionAttributes;
import com.aerofs.sp.server.IdentitySessionManager;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.ObjectUtils;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.common.impl.SecureRandomIdentifierGenerator;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.binding.encoding.HTTPRedirectDeflateEncoder;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.metadata.Endpoint;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.transport.http.HttpServletResponseAdapter;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.validation.ValidationException;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class SAMLAuthHandler implements IExternalAuthHandler {

    private static final Logger l = Loggers.getLogger(SAMLAuthHandler.class);
    private static final long serialVersionUID = 1L;

    private final IdentitySessionManager _identitySessionManager;

    public SAMLAuthHandler()
    {
        PooledJedisConnectionProvider jedis = new PooledJedisConnectionProvider();
        jedis.init_(LibParam.REDIS.AOF_ADDRESS.getHostName(),
                LibParam.REDIS.AOF_ADDRESS.getPort(), LibParam.REDIS.PASSWORD);
        _identitySessionManager = new IdentitySessionManager(jedis);

        try {
            DefaultBootstrap.bootstrap();
        } catch (ConfigurationException e) {
            // Catching checked exception and throwing unchecked exception here because without
            // bootstrapping being a success we cannot proceed any further.
            throw new RuntimeException("Failed to bootstrap SAML");
        }
    }

    /* Request related functions */
    private static Issuer getIssuer() throws NoSuchFieldException, IllegalAccessException
    {
        Issuer issuer = SAMLUtil.buildSAMLObject(Issuer.class);
        issuer.setValue(SAML.SP_ISSUER);
        return issuer;
    }

    private static NameIDPolicy getNameIdPolicy() throws NoSuchFieldException, IllegalAccessException
    {
        NameIDPolicy nameIDPolicy = SAMLUtil.buildSAMLObject(NameIDPolicy.class);
        nameIDPolicy.setAllowCreate(true);
        nameIDPolicy.setFormat(NameIDType.TRANSIENT);
        return nameIDPolicy;
    }

    private static Endpoint getIDPEndpoint() throws NoSuchFieldException, IllegalAccessException
    {
        SingleSignOnService endpoint = SAMLUtil.buildSAMLObject(SingleSignOnService.class);
        endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        endpoint.setLocation(SAML.ENDPOINT_URL);
        return endpoint;
    }

    private RequestedAuthnContext buildRequestedAuthnContext()
            throws NoSuchFieldException, IllegalAccessException
    {
        RequestedAuthnContext requestedAuthnContext = SAMLUtil.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);
        AuthnContextClassRef passwordAuthnContextClassRef = SAMLUtil.buildSAMLObject(AuthnContextClassRef.class);
        passwordAuthnContextClassRef.setAuthnContextClassRef(AuthnContext.PASSWORD_AUTHN_CTX);
        requestedAuthnContext.getAuthnContextClassRefs().add(passwordAuthnContextClassRef);
        return requestedAuthnContext;
    }

    private AuthnRequest buildAuthnRequest(String assertionConsumerUrl)
            throws NoSuchFieldException, IllegalAccessException, NoSuchAlgorithmException,
            IOException, ParserConfigurationException, MarshallingException, TransformerException
    {
        AuthnRequest authnRequest = SAMLUtil.buildSAMLObject(AuthnRequest.class);
        authnRequest.setIssueInstant(new DateTime());
        authnRequest.setDestination(SAML.ENDPOINT_URL);
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        authnRequest.setAssertionConsumerServiceURL(assertionConsumerUrl);
        authnRequest.setID(new SecureRandomIdentifierGenerator().generateIdentifier());
        authnRequest.setIssuer(getIssuer());
        authnRequest.setNameIDPolicy(getNameIdPolicy());
        authnRequest.setRequestedAuthnContext(buildRequestedAuthnContext());
        SAMLUtil.logSAMLObject(authnRequest, "Authentication Request");
        return authnRequest;
    }

    private void redirectUserWithRequest(HttpServletResponse resp, AuthnRequest authnRequest,
            String updateToken, String nextUrl) throws NoSuchFieldException,
            IllegalAccessException, MessageEncodingException
    {
        HttpServletResponseAdapter responseAdapter = new HttpServletResponseAdapter(resp, true);
        BasicSAMLMessageContext<SAMLObject, AuthnRequest, SAMLObject> context =
                new BasicSAMLMessageContext<SAMLObject, AuthnRequest, SAMLObject>();
        context.setPeerEntityEndpoint(getIDPEndpoint());
        context.setRelayState(marshallRelayState(updateToken, nextUrl));
        context.setOutboundSAMLMessage(authnRequest);
        context.setOutboundMessageTransport(responseAdapter);

        l.debug("Redirecting user to IDP");
        HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();
        encoder.encode(context);
    }

    private IdentitySessionAttributes getAssertionAttributes(Assertion assertion)
    {
        String email = null, firstName = null, lastName = null;
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            for (XMLObject attributeValue : attribute.getAttributeValues()) {
                // The name of the attributes returned in the assertion are supposed to
                // configured on the IDP side because they are IDP specific. In general, there are
                // 4 attributes username, first name, last name and email. We care only about the
                // latter 3. For max flexibility when dealing with attribute name's and their cases.
                // we convert them all to lower case and check if they contain the words
                // first, last or email. For example: one IDP might returned "firstname" as an
                // attribute name while another might return "FirstName" as an attribute name.
                String attributeLower = attribute.getName().toLowerCase();
                if (attributeLower.contains("first")) {
                    if (attributeValue instanceof XSAny)
                        firstName = ((XSAny) attributeValue).getTextContent();
                    else
                        firstName = ((XSString) attributeValue).getValue();
                } else if (attributeLower.contains("last")) {
                    if (attributeValue instanceof XSAny)
                        lastName = ((XSAny) attributeValue).getTextContent();
                    else
                        lastName = ((XSString) attributeValue).getValue();
                } else if (attributeLower.contains("email")) {
                    if (attributeValue instanceof XSAny)
                        email = ((XSAny) attributeValue).getTextContent();
                    else
                        email = ((XSString) attributeValue).getValue();
                }
            }
        }
        Preconditions.checkArgument(email != null, "Email cannot be null");
        l.debug("Received assertion attributes {} {} {}", firstName, lastName, email);
        return new IdentitySessionAttributes(email, firstName, lastName);
    }

    private String marshallRelayState(String updateToken, String onComplete)
    {
        return updateToken + " " + onComplete;
    }

    @Override
    public void handleAuthRequest(HttpServletRequest req, HttpServletResponse resp) throws Exception
    {
        String updateToken = req.getParameter(LibParam.Identity.IDENTITY_REQ_PARAM);
        String nextUrl = (String) ObjectUtils.defaultIfNull(req.getParameter(LibParam.Identity.ONCOMPLETE_URL), "files");
        try {
            AuthnRequest authnRequest = buildAuthnRequest(SAML.ASSERTION_CONSUMER_URL);
            l.info("Sending Authentication request to IDP. Expecting reply at: {}", SAML.ASSERTION_CONSUMER_URL);
            redirectUserWithRequest(resp, authnRequest, updateToken, nextUrl);
        } catch (Exception e) {
            l.error("failed to handle authentication request {}", e);
            throw e;
        }
    }

    /* Response related functions */
    private void verifyAssertionSignature(Assertion assertion)
            throws CertificateException, IOException, ValidationException
    {
        if (!assertion.isSigned()) {
            throw new ValidationException("The SAML Assertion was not signed");
        }

        SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
        profileValidator.validate(assertion.getSignature());

        String cert = getStringProperty("saml.idp.x509.certificate");
        X509Certificate x509cert = BaseSecUtil.newCertificateFromStream
                (new ByteArrayInputStream(cert.getBytes()));
        BasicX509Credential cred = new BasicX509Credential();
        cred.setEntityCertificate(x509cert);
        SignatureValidator sigValidator = new SignatureValidator(cred);
        sigValidator.validate(assertion.getSignature());
        l.info("SAML response signature verified");
    }

    @Override
    public void handleAuthResponse(HttpServletRequest req, HttpServletResponse resp)
            throws Exception
    {
        String responseMessage = req.getParameter("SAMLResponse");
        if (responseMessage == null) throw new IllegalArgumentException("Response doesn't contain SAMLResponse param");

        byte[] base64DecodedResponse = Base64.decode(responseMessage);
        ByteArrayInputStream is = new ByteArrayInputStream(base64DecodedResponse);
        try {
            String relayState = req.getParameter("RelayState");
            if (relayState == null) throw new IllegalArgumentException("Response doesn't contain RelayState param");
            String[] tokens = relayState.split(" ");
            if (tokens.length != 2) throw new IllegalArgumentException("Received invalid relay state");
            String delegateNonce = tokens[0];
            String onComplete = tokens[1];
            l.info("Received response from IDP with delegateNonce {} oncomplete URL {}",
                    delegateNonce, onComplete);

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = docBuilder.parse(is);
            Element element = document.getDocumentElement();
            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            XMLObject responseXmlObj = unmarshaller.unmarshall(element);
            Response response = (Response) responseXmlObj;

            // Verify assertion
            Assertion assertion = response.getAssertions().get(0);

            verifyAssertionSignature(assertion);
            SAMLUtil.logSAMLObject(assertion, "Assertion");
            req.setAttribute(LibParam.Identity.DELEGATE_NONCE, delegateNonce);

            _identitySessionManager.authenticateSession(
                    delegateNonce,
                    LibParam.Identity.SESSION_TIMEOUT,
                    getAssertionAttributes(assertion));

            resp.sendRedirect(onComplete);
        } catch (Exception e) {
            l.error("failed to handle auth response {}", e);
            throw e;
        }

    }
}

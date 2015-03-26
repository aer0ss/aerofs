package com.aerofs.ca.server.resources;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ca.server.TestCAServer;
import com.aerofs.ca.utils.CertificateUtils;
import com.aerofs.ca.utils.KeyUtils;
import com.google.common.net.HttpHeaders;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.security.KeyPair;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestCAResource
{
    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new TestCAServer());

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturnCACert()
            throws Exception
    {
        Response resp =
                RestAssured
                .given()
                .get(TestCAServer.getCAUrl() + "/cacert.pem");
        resp
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertTrue(resp.getBody().asString().contains("-----BEGIN CERTIFICATE-----\n"));
        X509CertificateHolder crt = CertificateUtils.pemToCert(resp.getBody().asByteArray());
        assertTrue(crt.isValidOn(new Date()));
        assertEquals(crt.getVersionNumber(), 3);

        // checking the cert against itself here, not sure if there's any point
        ContentVerifierProvider caTrustStore = new BcRSAContentVerifierProviderBuilder(new DefaultDigestAlgorithmIdentifierFinder()).build(crt);
        assertTrue(crt.isSignatureValid(caTrustStore));

        Set<ASN1ObjectIdentifier> criticals = crt.getCriticalExtensionOIDs();
        assertEquals(1, criticals.size());
        assertEquals(Extension.basicConstraints, criticals.iterator().next());
        BasicConstraints basic = BasicConstraints.getInstance(crt.getExtension(Extension.basicConstraints).getParsedValue());
        assertEquals(basic, new BasicConstraints(true));

        Set<ASN1ObjectIdentifier> nonCriticals = crt.getNonCriticalExtensionOIDs();
        assertEquals(nonCriticals.size(), 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSignRequest()
            throws Exception
    {
        Response caCertPEM =
                RestAssured
                        .given()
                        .header(HttpHeaders.AUTHORIZATION , getAuthorizationHeader("test"))
                        .get(TestCAServer.getCAUrl() + "/cacert.pem");
        caCertPEM.then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        X509CertificateHolder cert = CertificateUtils.pemToCert(caCertPEM.getBody().asByteArray());
        ContentVerifierProvider caTrustStore = new BcRSAContentVerifierProviderBuilder(new DefaultDigestAlgorithmIdentifierFinder()).build(cert);

        KeyPair signedKeys = KeyUtils.newKeyPair();
        X500Name subject = new X500Name("C=US, ST=California, L=San Francisco, O=aerofs.com, CN=adevice");
        SubjectPublicKeyInfo publicKey = new SubjectPublicKeyInfo(ASN1Sequence.getInstance(signedKeys.getPublic().getEncoded()));
        PKCS10CertificationRequestBuilder csrBuilder = new PKCS10CertificationRequestBuilder(subject, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signedKeys.getPrivate());
        PKCS10CertificationRequest csr = csrBuilder.build(signer);
        Response resp =
                RestAssured
                .given()
                .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                .body(CertificateUtils.csrToPEM(csr))
                .post(TestCAServer.getCAUrl() + "?service.com");
        resp
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        X509CertificateHolder crt = CertificateUtils.pemToCert(resp.getBody().asByteArray());

        // check info matches
        assertTrue(crt.isValidOn(new Date()));
        assertEquals(crt.getSubject(), subject);
        assertEquals(crt.getVersionNumber(), 3);
        assertEquals(crt.getSubjectPublicKeyInfo(), publicKey);
        assertTrue(crt.isSignatureValid(caTrustStore));

        // check expected extensions
        Set<ASN1ObjectIdentifier> criticals = crt.getCriticalExtensionOIDs();
        assertEquals(1, criticals.size());
        assertEquals(Extension.basicConstraints, criticals.iterator().next());
        BasicConstraints basic = BasicConstraints.getInstance(crt.getExtension(Extension.basicConstraints).getParsedValue());
        assertEquals(basic, new BasicConstraints(false));

        Set<ASN1ObjectIdentifier> nonCriticals = crt.getNonCriticalExtensionOIDs();
        assertEquals(nonCriticals.size(), 2);
    }

    @Test
    public void shouldRequireServiceAuth()
    {
        RestAssured
                .given()
                .body("doesn't matter")
                .post(TestCAServer.getCAUrl() + "?service.com")
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void shouldReturn400OnBadCSR()
            throws Exception
    {
        RestAssured
                .given()
                .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                .body("not a valid csr")
                .post(TestCAServer.getCAUrl() + "?service.com")
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturn400OnNoServiceName()
            throws Exception
    {
        RestAssured
                .given()
                .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                .body(CertificateUtils.csrToPEM(newCsr()))
                .post(TestCAServer.getCAUrl())
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldHaveDifferentSerialNumbers()
            throws Exception
    {
        PKCS10CertificationRequest csr1 = newCsr();
        Response resp =
                RestAssured
                        .given()
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                        .body(CertificateUtils.csrToPEM(csr1))
                        .post(TestCAServer.getCAUrl() + "?service.com");
        resp.then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());

        X509CertificateHolder crt1 = CertificateUtils.pemToCert(resp.getBody().asByteArray());

        PKCS10CertificationRequest csr2 = newCsr();
        resp =
                RestAssured
                        .given()
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                        .body(CertificateUtils.csrToPEM(csr2))
                        .post(TestCAServer.getCAUrl() + "?service.com");
        resp.then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        X509CertificateHolder crt2 = CertificateUtils.pemToCert(resp.getBody().asByteArray());

        assertTrue("serial numbers must be unique per CA", !crt1.getSerialNumber().equals(crt2.getSerialNumber()));
    }

    @Test
    public void shouldAddSubAltNameForIPAddr()
            throws Exception
    {
        String ipAddr = "1.2.3.4";
        KeyPair signedKeys = KeyUtils.newKeyPair();
        X500Name subject = new X500Name("C=US, ST=California, L=San Francisco, O=aerofs.com, CN=" + ipAddr);
        SubjectPublicKeyInfo publicKey = new SubjectPublicKeyInfo(ASN1Sequence.getInstance(signedKeys.getPublic().getEncoded()));
        PKCS10CertificationRequestBuilder csrBuilder = new PKCS10CertificationRequestBuilder(subject, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signedKeys.getPrivate());
        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        Response resp =
                RestAssured
                        .given()
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("test"))
                        .body(CertificateUtils.csrToPEM(csr))
                        .post(TestCAServer.getCAUrl() + "?service.com");
        resp
                .then()
                .statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        X509CertificateHolder crt = CertificateUtils.pemToCert(resp.getBody().asByteArray());
        Extension extension = crt.getExtension(Extension.subjectAlternativeName);
        assertTrue(extension != null);
        assertEquals(extension.getParsedValue(), new GeneralName(GeneralName.iPAddress, ipAddr));
    }

    private PKCS10CertificationRequest newCsr()
            throws Exception
    {
        KeyPair signedKeys = KeyUtils.newKeyPair();
        X500Name subject = new X500Name("C=US, ST=California, L=San Francisco, O=aerofs.com, CN=adevice");
        SubjectPublicKeyInfo publicKey = new SubjectPublicKeyInfo(ASN1Sequence.getInstance(signedKeys.getPublic().getEncoded()));
        PKCS10CertificationRequestBuilder csrBuilder = new PKCS10CertificationRequestBuilder(subject, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signedKeys.getPrivate());
        return csrBuilder.build(signer);
    }

    private String getAuthorizationHeader(String service, String deploymentSecret)
    {
        return com.aerofs.auth.client.shared.AeroService.getHeaderValue(service, deploymentSecret);
    }

    private String getAuthorizationHeader(String service)
    {
        return getAuthorizationHeader(service, TestCAServer.testDeploymentSecret);
    }
}

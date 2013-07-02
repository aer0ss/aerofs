package com.aerofs.lib.configuration;

/**
 * The tests related to dynamic configuration need to be run against a set of servers on
 *   the network since we do not have an embedded web server for testing purpose.
 *
 * These values will need to be updated when the dynamic configuration tests are run to
 *   match the settings on the servers.
 */
public class ConfigurationTestConstants
{
    // this should be the URL to the configuration service
    static final String URL = "https://config.syncfs.com";
    // this should be a well-formed URL pointing to an non-existing service
    static final String BAD_URL = "https://cnofig.syncfs.com";

    // this should be the SSL certificate used by the configuration service and SP.
    static final String CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDSjCCAjICARkwDQYJKoZIhvcNAQELBQAwgYAxDzANBgNVBAMTBkFlcm9GUzEL\n" +
            "MAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDVNhbiBG\n" +
            "cmFuY2lzY28xEzARBgNVBAoTCmFlcm9mcy5jb20xHjAcBgkqhkiG9w0BCQEWD3Rl\n" +
            "YW1AYWVyb2ZzLmNvbTAgGA8yMDEzMDYyNzIxNDg1NFoXDTE0MDYyODIxNDg1NFow\n" +
            "UzELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRMwEQYDVQQKDAphZXJvZnMuY29t\n" +
            "MQswCQYDVQQLDAJuYTEVMBMGA1UEAwwMKi5zeW5jZnMuY29tMIIBIjANBgkqhkiG\n" +
            "9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0iGe+FlPe5g4KyTqoeV5b52dbxVNBhMux6bG\n" +
            "j8SWAtr2ZDOEZNp9UN8RbzUpw5uZOLiGKxrUeR3kUTmlmK2RFZ8+X+TdgaUIprr7\n" +
            "EnfG8PLLwmxwE1Hc1WShYfAeGfac+cxhSkqvnq6gDB7dF8h0IdZttmCeDobHSTrZ\n" +
            "Zy9PxbeVzjQeoS4pY7Rg5elAdQKSZRN8o4fCogR0XC7ZaWbII7UPrM5LHqMxnQF8\n" +
            "WEr6a/DXaTnViMmpefCT8MAZBxfAeLfxhV4HGiMa+d+kzKmH/leEt0pdYNmM4jeg\n" +
            "sBqdtAPo9jE8SrBT7TGBi5PDFHypgd1HmQZIqydK6R3G7RgDKwIDAQABMA0GCSqG\n" +
            "SIb3DQEBCwUAA4IBAQDN1O0S/3K4xHIu0FCEYVUetlzZFDHbvtr4EP+ygul61usl\n" +
            "p7eWrQCSjAgXYVowG13E8BvwEW3f0UydQgDPsGqJUUyMCikzyTEgS7aV4Fq8nDQj\n" +
            "nFCXGGudRxDdSoAGhPYLCY074qNlmQHSklD515Cctb0x9Ev4vs1mHcTSQhxhoCGm\n" +
            "9CcSar777e2dHObA3WagUoPjqyXlFejxdZF0v0xO+dYXgW/LHVq5xx/GCIc/TeZc\n" +
            "Vq6OoGID9Uslmw5mZ6GmH0uMiD3NItVjgfWEnqVdpq9EH1jAG3tVolSgxOpnz1l4\n" +
            "KRwMHtv8xAU4XtNDAS0GLINAehzMtUxBExMUbORF\n" +
            "-----END CERTIFICATE-----";
    // this should be valid SSL certificate but not related to the one used by the configuration
    //   service or SP
    static final String WRONG_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDSjCCAjICAwIJhzANBgkqhkiG9w0BAQsFADCBgDEPMA0GA1UEAxMGQWVyb0ZT\n" +
            "MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNU2Fu\n" +
            "IEZyYW5jaXNjbzETMBEGA1UEChMKYWVyb2ZzLmNvbTEeMBwGCSqGSIb3DQEJARYP\n" +
            "dGVhbUBhZXJvZnMuY29tMCAYDzIwMTMwNjI3MDEyMTU3WhcNMTQwNjI4MDEyMTU3\n" +
            "WjBRMQswCQYDVQQGEwJVUzELMAkGA1UECAwCQ0ExEzARBgNVBAoMCmFlcm9mcy5j\n" +
            "b20xCzAJBgNVBAsMAm5hMRMwEQYDVQQDDAoqLmFzZGYuY29tMIIBIjANBgkqhkiG\n" +
            "9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy/LWyu266p+i6ZTD196AiTxipqn9qG0r5TTO\n" +
            "BnjUzSEK8+fDHwHHBN5kJwOIa6XMKi35qiebH7r1+FzSj/K+gugW2YTST3iR1s9g\n" +
            "8UgLM47/V+l+q4MyHJfpyqOwSDo4PEsKSwmssejJ5njYJ1eF43uZ2QhWonrBx9ew\n" +
            "l37JImzatBfOf8NJxDUEBjI5iLOJGAwcPAtVrCBjkub9Sm80WLmeszVwYZUMW+v+\n" +
            "bxxLhDt5h98bgVNHKKJxpoGh8KXO3YBZ4+lDOfcflz8VKG+Xq9LXwaljbzElDK9I\n" +
            "z3QDuVPZEIU3G3pGqYD5Vc+bNvTzikJuA7ixr61vD7fXbQs+OQIDAQABMA0GCSqG\n" +
            "SIb3DQEBCwUAA4IBAQATtJ6i3WS/Vwp0w2UVfB7VsJs7RBLXqDlMB/2KjNVtHgR5\n" +
            "TvbnwXrrFH/CEuv0jTvekAY77iKey53urruS5seTBRjZTSsZWgOJrxvdPT/0UZom\n" +
            "uWGSJZi3oCne24exaLMwlWaDiv/dKh/0EtMW2nzg9evYPrTX9ALyzWWyBy8EKs8f\n" +
            "g3ZyEHohLArac78eRfK14bAwWDJd/nthjdugoZjojAIDwJsGLH61bI3Qe1shDjKi\n" +
            "oanIUT+IUQTAS+ZkvOHgXBdgi0HZ3gBRUcYCPLKuJq2iRfVesuWovor9uf+ZhQAz\n" +
            "pfYvd/PXV6UExcdU0Bzkpkniy9bFTl0QFcEIjtUq\n" +
            "-----END CERTIFICATE-----";

    // this should be the name of a property available only from the configuration service
    static final String TEST_PROP = "updater.installer.url";

    // this should be the value of the test property.
    static final String TEST_PROP_VALUE = "https://admin.syncfs.com/static";
}

package com.aerofs.polaris.throughput.client;

import com.aerofs.ids.core.Identifiers;
import com.aerofs.overload.HttpRequestProvider;
import com.aerofs.overload.driver.OverloadDriver;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.commons.cli.CommandLine;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Client extends OverloadDriver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }


    public static void main(String[] args) {
        Client client = new Client();
        client.run(args);
    }

    @Override
    protected HttpRequestProvider newConfiguredRequestProvider(CommandLine commandLine) throws IllegalArgumentException {
        final String root = Identifiers.newRandomSharedFolder();
        return new HttpRequestProvider() {

            @Override
            public FullHttpRequest getRequest(ByteBufAllocator allocator) throws Exception {
                String random = Long.toHexString(Double.doubleToLongBits(Math.random()));

                String device = Identifiers.newRandomDevice();
                InsertChild operation = new InsertChild(Identifiers.newRandomObject(), ObjectType.FILE, random);
                ByteBuf content = allocator.buffer();
                ByteBufOutputStream outputStream = new ByteBufOutputStream(content);
                try {
                    MAPPER.writeValue(outputStream, operation);

                    DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/objects/" + root, content);
                    request.headers().add("Verify", "SUCCESS");
                    request.headers().add("DName", "CN=" + getCertificateCName(device, "allen@aerofs.com"));
                    request.headers().add("Authorization", String.format("Aero-Device-Cert %s allen@aerofs.com", device));
                    request.headers().add("Content-Length", content.readableBytes());
                    request.headers().add("Content-Type", "application/json");

                    return request;
                } catch (IOException e) {
                    content.release();
                    outputStream.close();
                    throw e;
                }
            }
        };
    }

    //
    // FIXME (AG): this is a straight-up copy of the code in SecurityContexts (split it out!)
    //

    private static final String HASH_FUNCTION = "SHA-256";

    private static char[] ALPHABET = {
            'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p',
    };

    public static String getCertificateCName(String did, String user) {
        return alphabetEncode(hash(user.getBytes(Charsets.UTF_8), BaseEncoding.base16().lowerCase().decode(did)));
    }

    private static String alphabetEncode(byte[] bs) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bs) {
            int hi = (b >> 4) & 0xF;
            int lo = b & 0xF;
            sb.append(ALPHABET[hi]);
            sb.append(ALPHABET[lo]);
        }

        return sb.toString();
    }

    public static byte[] hash(byte[] ... blocks) {
        try {
            MessageDigest instance = MessageDigest.getInstance(HASH_FUNCTION);

            for (byte[] block : blocks) {
                instance.update(block);
            }

            return instance.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("cannot initialize hash function " + HASH_FUNCTION);
        }
    }

}

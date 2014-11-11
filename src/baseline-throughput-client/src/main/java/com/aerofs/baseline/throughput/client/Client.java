package com.aerofs.baseline.throughput.client;

import com.aerofs.overload.HttpRequestProvider;
import com.aerofs.overload.driver.OverloadDriver;
import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

import java.io.IOException;

public final class Client extends OverloadDriver {

    public static void main(String[] args) {
        Client client = new Client();
        client.run(args);
    }

    @SuppressWarnings("AccessStaticViaInstance")
    @Override
    protected void addCommandLineOptions(Options options) {
        options.addOption(OptionBuilder.hasArg().isRequired().withLongOpt("mode").withDescription("mode in which to run the throughput testing client (GET, JSON, POLL)").create("m"));
    }

    @Override
    protected HttpRequestProvider newConfiguredRequestProvider(CommandLine commandLine) throws IllegalArgumentException {
        String mode = commandLine.getOptionValue("mode");
        switch (mode) {
        case "GET":
            return newGetRequestProvider();
        case "JSON":
            return newJsonRequestProvider();
        case "POLL":
            return newPollingRequestProvider();
        default:
            throw new IllegalArgumentException("unrecognized mode " + mode);
        }
    }

    private HttpRequestProvider newGetRequestProvider() {
        return new HttpRequestProvider() {

            @Override
            public FullHttpRequest getRequest(ByteBufAllocator allocator) throws Exception {
                return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/get");
            }
        };
    }

    private HttpRequestProvider newJsonRequestProvider() {
        return new HttpRequestProvider() {

            @Override
            public FullHttpRequest getRequest(ByteBufAllocator allocator) throws Exception {
                ByteBuf content = allocator.buffer();
                ByteBufOutputStream outputStream = new ByteBufOutputStream(content);
                try {
                    outputStream.write("{\"string\":\"astring\",\"number\":123431589}".getBytes(Charsets.UTF_8));
                    return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/json", content);
                } catch (IOException e) {
                    content.release();
                    outputStream.close();
                    throw e;
                }
            }
        };
    }

    private HttpRequestProvider newPollingRequestProvider() {
        return new HttpRequestProvider() {

            @Override
            public FullHttpRequest getRequest(ByteBufAllocator allocator) throws Exception {
                return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/polling");
            }
        };
    }
}

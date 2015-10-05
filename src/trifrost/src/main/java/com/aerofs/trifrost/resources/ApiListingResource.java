package com.aerofs.trifrost.resources;

import io.swagger.annotations.ApiOperation;
import io.swagger.config.FilterFactory;
import io.swagger.config.Scanner;
import io.swagger.config.ScannerFactory;
import io.swagger.config.SwaggerConfig;
import io.swagger.core.filter.SpecFilter;
import io.swagger.core.filter.SwaggerSpecFilter;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.JaxrsScanner;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.models.Swagger;
import io.swagger.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.util.*;

@Path("/")
public class ApiListingResource {
    Logger LOGGER = LoggerFactory.getLogger(ApiListingResource.class);

    static boolean initialized = false;
    static Swagger swagger = null;

    @Context
    ServletContext context;

    protected synchronized Swagger scan(Application app, ServletConfig sc) {
        Scanner scanner = ScannerFactory.getScanner();
        LOGGER.info("using scanner " + scanner);

        if (scanner != null) {
            SwaggerSerializers.setPrettyPrint(scanner.getPrettyPrint());
            if (context != null) {
                swagger = (Swagger) context.getAttribute("swagger");
            } else {
                swagger = new Swagger();
            }
            Set<Class<?>> classes;
            if (scanner instanceof JaxrsScanner) {
                JaxrsScanner jaxrsScanner = (JaxrsScanner) scanner;
                classes = jaxrsScanner.classesFromContext(app, sc);
            } else {
                classes = scanner.classes();
            }
            if (classes != null) {
                Reader reader = new Reader(swagger);
                swagger = reader.read(classes);
                if (scanner instanceof SwaggerConfig)
                    swagger = ((SwaggerConfig) scanner).configure(swagger);
                else {
                    SwaggerConfig configurator = (SwaggerConfig) context
                            .getAttribute("reader");
                    if (configurator != null) {
                        LOGGER.info("configuring swagger with " + configurator);
                        configurator.configure(swagger);
                    } else
                        LOGGER.info("no configurator");
                }

            }
        }
        initialized = true;
        return swagger;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/swagger.json")
    @ApiOperation(value = "The swagger definition in JSON", hidden = true)
    public Response getListingJson(@Context Application app,
                                   @Context ServletConfig sc, @Context HttpHeaders headers,
                                   @Context UriInfo uriInfo) {
        if (!initialized)
            swagger = scan(app, sc);
        if (swagger != null) {
            SwaggerSpecFilter filterImpl = FilterFactory.getFilter();
            if (filterImpl != null) {
                SpecFilter f = new SpecFilter();
                swagger = f.filter(swagger, filterImpl,
                        getQueryParams(uriInfo.getQueryParameters()),
                        getCookies(headers), getHeaders(headers));
            }
            return Response.ok().entity(swagger).build();
        } else
            return Response.status(404).build();
    }

    @GET
    @Produces("application/yaml")
    @Path("/swagger.yaml")
    @ApiOperation(value = "The swagger definition in YAML", hidden = true)
    public Response getListingYaml(@Context Application app,
                                   @Context ServletConfig sc, @Context HttpHeaders headers,
                                   @Context UriInfo uriInfo) {
        // Swagger swagger = (Swagger) context.getAttribute("swagger");
        if (!initialized)
            swagger = scan(app, sc);
        try {
            if (swagger != null) {
                SwaggerSpecFilter filterImpl = FilterFactory.getFilter();
                LOGGER.info("using filter " + filterImpl);
                if (filterImpl != null) {
                    SpecFilter f = new SpecFilter();
                    swagger = f.filter(swagger, filterImpl,
                            getQueryParams(uriInfo.getQueryParameters()),
                            getCookies(headers), getHeaders(headers));
                }

                String yaml = Yaml.mapper().writeValueAsString(swagger);
                String[] parts = yaml.split("\n");
                StringBuilder b = new StringBuilder();
                for (String part : parts) {
                    int pos = part.indexOf("!<");
                    int endPos = part.indexOf(">");
                    b.append(part);
                    b.append("\n");
                }
                return Response.ok().entity(b.toString()).type("text/plain")
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.status(404).build();
    }

    protected Map<String, List<String>> getQueryParams(
            MultivaluedMap<String, String> params) {
        Map<String, List<String>> output = new HashMap<String, List<String>>();
        if (params != null) {
            for (String key : params.keySet()) {
                List<String> values = params.get(key);
                output.put(key, values);
            }
        }
        return output;
    }

    protected Map<String, String> getCookies(HttpHeaders headers) {
        Map<String, String> output = new HashMap<String, String>();
        if (headers != null) {
            for (String key : headers.getCookies().keySet()) {
                Cookie cookie = headers.getCookies().get(key);
                output.put(key, cookie.getValue());
            }
        }
        return output;
    }

    protected Map<String, List<String>> getHeaders(HttpHeaders headers) {
        Map<String, List<String>> output = new HashMap<String, List<String>>();
        if (headers != null) {
            for (String key : headers.getRequestHeaders().keySet()) {
                List<String> values = headers.getRequestHeaders().get(key);
                output.put(key, values);
            }
        }
        return output;
    }
}

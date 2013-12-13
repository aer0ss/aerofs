package com.aerofs.bifrost.oaaas.resource;

import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ClientResponseObject;
import com.aerofs.bifrost.oaaas.model.ListClientsResponse;
import com.aerofs.bifrost.oaaas.model.NewClientRequest;
import com.aerofs.bifrost.oaaas.model.NewClientResponse;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Resource for handling all calls related to client management.
 */
@Path("/clients")
public class ClientsResource
{
    private static final Logger l = LoggerFactory.getLogger(ClientsResource.class);

    @Inject
    private ClientRepository clientRepository;

    @Inject
    private ResourceServerRepository resourceServerRepository;

    @Inject
    private SessionFactory sessionFactory;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/x-www-form-urlencoded")
    public Response newClient(final MultivaluedMap<String, String> formParameters)
    {
        /**
         * FIXME: If we don't wrap this in a try/catch, the server will not return 500,
         * it will just close the connection. This is problem.
         */
        try {
            l.info("POST /clients {}", formParameters);

            NewClientRequest ncr = NewClientRequest.fromMultiValuedFormParameters(formParameters);
            if (ncr.getClientName() == null
                    || ncr.getResourceServerKey() == null
                    || ncr.getRedirectUri() == null) {
                l.warn("client_name: {}, resource_server_key: {}, redirect_uri: {}",
                        ncr.getClientName(),
                        ncr.getResourceServerKey(),
                        ncr.getRedirectUri());
                return Response.status(Status.BAD_REQUEST).build();
            }

            ResourceServer resourceServer = resourceServerRepository.findByKey(ncr.getResourceServerKey());
            if (resourceServer == null) {
                l.warn("no resource server with key: {}", ncr.getResourceServerKey());
                return Response.status(Status.BAD_REQUEST).build();
            }

            String clientID = UUID.randomUUID().toString();
            String secret = UUID.randomUUID().toString();

            Client client = new Client();
            client.setName(ncr.getClientName());
            client.setResourceServer(resourceServer);
            client.setDescription(ncr.getDescription());
            client.setContactEmail(ncr.getContactEmail());
            client.setContactName(ncr.getContactName());
            client.setClientId(clientID);
            client.setSecret(secret);
            client.setScopes(new HashSet<String>(Arrays.asList("readonly")));
            client.getRedirectUris().add(ncr.getRedirectUri());

            resourceServer.getClients().add(client);

            clientRepository.save(client);
            resourceServerRepository.save(resourceServer);
            sessionFactory.getCurrentSession().flush();

            NewClientResponse response = new NewClientResponse(clientID, secret);
            return Response.ok().entity(response).build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listClients()
    {
        try {
            l.debug("GET /clients");

            List<Client> clientList = clientRepository.listAll();

            List<ClientResponseObject> clientResponseObjectList =
                    new ArrayList<ClientResponseObject>(clientList.size());
            for (Client client : clientList) {
                clientResponseObjectList.add(new ClientResponseObject(
                        client.getClientId(),
                        client.getResourceServer().getKey(),
                        client.getName(),
                        client.getDescription(),
                        client.getContactEmail(),
                        client.getContactName(),
                        client.getRedirectUris().get(0),
                        client.getSecret()));
            }

            ListClientsResponse response = new ListClientsResponse(clientResponseObjectList);
            return Response.ok().entity(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            l.error(e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET @Path("{clientId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clientInfo(@PathParam("clientId") String clientId)
    {
        try {
            l.debug("GET /clients/{}", clientId);

            Client client = clientRepository.findByClientId(clientId);

            if (client == null) {
                l.warn("client not found: {}", clientId);
                return Response.status(Status.NOT_FOUND).build();
            }

            return Response.ok().entity(new ClientResponseObject(
                    client.getClientId(),
                    client.getResourceServer().getKey(),
                    client.getName(),
                    client.getDescription(),
                    client.getContactEmail(),
                    client.getContactName(),
                    client.getRedirectUris().get(0),
                    client.getSecret())).build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DELETE @Path("{clientId}")
    public Response deleteClient(@PathParam("clientId") String clientId)
    {
        try {
            l.info("DELETE /clients/{}", clientId);

            Client client = clientRepository.findByClientId(clientId);
            if (client == null) {
                l.warn("clientRepository.findByClientId({}) failed", clientId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            ResourceServer resourceServer = client.getResourceServer();
            resourceServer.getClients().remove(client);

            /**
             * TODO: should we delete access_tokens associated with client? Verifying tokens
             * will fail as long as we delete the client.
             */

            clientRepository.delete(client);
            resourceServerRepository.save(resourceServer);
            sessionFactory.getCurrentSession().flush();

            return Response.ok().build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}

package com.aerofs.bifrost.oaaas.model;

import java.util.List;

/**
 * Representation of the client list response as defined in bifrost_api.md
 */
public class ListClientsResponse
{
    List<ClientResponseObject> clients;

    public ListClientsResponse(List<ClientResponseObject> clients)
    {
        this.clients = clients;
    }

    public List<ClientResponseObject> getClients()
    {
        return clients;
    }

    public void setClients(List<ClientResponseObject> clients)
    {
        this.clients = clients;
    }
}

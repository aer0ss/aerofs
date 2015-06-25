package com.aerofs.polaris.deserializer;

import com.aerofs.rest.api.Folder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class FolderDeserializer extends StdDeserializer<Folder>
{
    private static final long serialVersionUID = -2018877680784383623L;

    public FolderDeserializer() {
        super(Folder.class);
    }

    @Override
    public Folder deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException
    {
        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        ObjectNode root = mapper.readTree(jp);
        Preconditions.checkArgument(root != null, "empty Operation object");
        Iterator<Map.Entry<String, JsonNode>> elementsIterator = root.fields();
        String id = null;
        String name = null;
        String parent = null;
        String sid = null;
        while (elementsIterator.hasNext()) {
            Map.Entry<String, JsonNode> element = elementsIterator.next();
            String key = element.getKey();
            if (key.equals("name")) {
                name = element.getValue().asText();
            }
            if (key.equals("parent")) {
                parent = element.getValue().asText();
            }
        }
        return new Folder(null, name, parent, null, null, null);
    }

}

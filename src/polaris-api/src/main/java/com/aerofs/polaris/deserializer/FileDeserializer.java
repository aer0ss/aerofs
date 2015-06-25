package com.aerofs.polaris.deserializer;

import com.aerofs.rest.api.File;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class FileDeserializer extends StdDeserializer<File>
{
    private static final long serialVersionUID = -2018877680784383623L;

    public FileDeserializer()
    {
        super(File.class);
    }

    @Override
    public File deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException
    {
        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        ObjectNode root = mapper.readTree(jp);
        Preconditions.checkArgument(root != null, "empty Operation object");
        Iterator<Map.Entry<String, JsonNode>> elementsIterator = root.fields();
        String name = null;
        String parent = null;

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
        return new File(null, name, parent, null, null, null, null, null, null);
    }

}

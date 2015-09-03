package com.aerofs.polaris.api.operation;

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

public final class OperationDeserializer extends StdDeserializer<Operation> {

    private static final long serialVersionUID = -2018877680784383623L;

    public OperationDeserializer() {
        super(Operation.class);
    }

    @Override
    public Operation deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        Class<? extends Operation> operationClass = null;

        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        ObjectNode root = mapper.readTree(jp);
        Preconditions.checkArgument(root != null, "empty Operation object");

        Iterator<Map.Entry<String, JsonNode>> elementsIterator = root.fields();
        while (elementsIterator.hasNext()) {
            Map.Entry<String, JsonNode> element = elementsIterator.next();
            String name = element.getKey();

            if (name.equals(Operation.TYPE_FIELD_NAME)) {
                String value = element.getValue().textValue();
                for (OperationType type : OperationType.values()) {
                    if (type.name().equalsIgnoreCase(value)) {
                        operationClass = getOperationClass(type);
                    }
                }
            }
        }

        if (operationClass == null) {
            throw new IllegalArgumentException("unrecognized Operation object");
        }

        return mapper.treeToValue(root, operationClass);
    }

    private Class<? extends Operation> getOperationClass(OperationType operationType) {
        switch (operationType) {
            case INSERT_CHILD:
                return InsertChild.class;
            case MOVE_CHILD:
                return MoveChild.class;
            case REMOVE_CHILD:
                return RemoveChild.class;
            case UPDATE_CONTENT:
                return UpdateContent.class;
            case SHARE:
                return Share.class;
            case RESTORE:
                return Restore.class;
            default:
                throw new IllegalArgumentException("unrecognized operation type " + operationType);
        }
    }
}

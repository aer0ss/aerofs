package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@JsonPropertyOrder({"type"}) // this field is always serialized first
public abstract class Operation {

    @JsonIgnore
    private static final String TYPE_FIELD_NAME = "type";

    @JsonProperty(TYPE_FIELD_NAME)
    public final OperationType type;

    protected Operation(OperationType type) {
        this.type = type;
    }

    public static void registerDeserializer(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule("PolymorphicOperationDeserializerModule", new Version(1, 0, 0, null, null, null));
        module.addDeserializer(Operation.class, new Deserializer());
        mapper.registerModule(module);
    }

    public static final class Deserializer extends StdDeserializer<Operation> {

        private static final long serialVersionUID = -2018877680784383623L;

        public Deserializer() {
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

                if (name.equals(TYPE_FIELD_NAME)) {
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
                default:
                    throw new IllegalArgumentException("unrecognized operation type " + operationType);
            }
        }
    }
}

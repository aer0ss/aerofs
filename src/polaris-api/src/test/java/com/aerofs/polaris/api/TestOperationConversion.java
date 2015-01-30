package com.aerofs.polaris.api;

import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;

public class TestOperationConversion {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOperationConversion.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public TestOperationConversion() {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        Operation.registerDeserializer(mapper);
    }

    @Test
    public void shouldSerializeAndDeserializeInsertChild() throws IOException {
        InsertChild insertChild = new InsertChild("CHILD", ObjectType.FOLDER, "CHILD NAME");

        String serialized = mapper.writeValueAsString(insertChild);
        LOGGER.info("insert_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(insertChild));
    }

    @Test
    public void shouldSerializeAndDeserializeRemoveChild() throws IOException {
        RemoveChild removeChild = new RemoveChild("CHILD");

        String serialized = mapper.writeValueAsString(removeChild);
        LOGGER.info("remove_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(removeChild));
    }

    @Test
    public void shouldSerializeAndDeserializeMoveChild() throws IOException {
        MoveChild moveChild = new MoveChild("CHILD", "NEW PARENT", "CHILD NAME");

        String serialized = mapper.writeValueAsString(moveChild);
        LOGGER.info("move_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(moveChild));
    }

    @Test
    public void shouldSerializeAndDeserializeUpdateContent() throws IOException {
        UpdateContent updateContent = new UpdateContent(1, "HASH", 100, System.currentTimeMillis());

        String serialized = mapper.writeValueAsString(updateContent);
        LOGGER.info("update_content:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(updateContent));
    }
}
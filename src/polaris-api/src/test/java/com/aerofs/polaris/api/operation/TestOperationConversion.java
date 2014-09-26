package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        Operation.registerDeserializer(mapper);
    }

    @Test
    public void shouldSerializeAndDeserializeInsertChild() throws IOException {
        InsertChild insertChild = new InsertChild();
        insertChild.child = "CHILD";
        insertChild.childName = "CHILD NAME";

        String serialized = mapper.writeValueAsString(insertChild);
        LOGGER.info("insert_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(insertChild));
    }

    @Test
    public void shouldSerializeAndDeserializeRemoveChild() throws IOException {
        RemoveChild removeChild = new RemoveChild();
        removeChild.child = "CHILD";

        String serialized = mapper.writeValueAsString(removeChild);
        LOGGER.info("remove_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(removeChild));
    }

    @Test
    public void shouldSerializeAndDeserializeMoveChild() throws IOException {
        MoveChild moveChild = new MoveChild();
        moveChild.child = "CHILD";
        moveChild.newParent = "NEW PARENT";
        moveChild.newChildName = "CHILD NAME";

        String serialized = mapper.writeValueAsString(moveChild);
        LOGGER.info("move_child:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(moveChild));
    }

    @Test
    public void shouldSerializeAndDeserializeUpdateContent() throws IOException {
        UpdateContent updateContent = new UpdateContent();
        updateContent.localVersion = 1;
        updateContent.contentHash = "HASH";
        updateContent.contentSize = 100;
        updateContent.contentMTime = System.currentTimeMillis();

        String serialized = mapper.writeValueAsString(updateContent);
        LOGGER.info("update_content:{}", serialized);

        Operation operation = mapper.readValue(serialized, Operation.class);
        assertThat(operation, Matchers.<Operation>equalTo(updateContent));
    }
}
package com.aerofs.polaris.logical;

import com.aerofs.baseline.Task;
import com.aerofs.baseline.admin.TasksResource;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.Map;

public final class TreeTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeTask.class);

    private final LogicalObjectStore logicalObjectStore;
    private final ObjectMapper mapper;

    public TreeTask(LogicalObjectStore logicalObjectStore, ObjectMapper mapper) {
        this.logicalObjectStore = logicalObjectStore;
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return "tree";
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter outputWriter) throws Exception {
        final String root = queryParameters.getFirst("root");
        final ObjectNode forest = mapper.createObjectNode();

        logicalObjectStore.inTransaction(new Transactional<Object>() {
            @Override
            public Void execute(DAO dao) throws Exception {
                if (root != null) {
                    dumpObjects(dao, root);
                } else {
                    dumpObjects(dao);
                }

                return null;
            }

            private void dumpObjects(DAO dao) {
                // iterate over all known roots
                ResultIterator<String> iterator = dao.objectTypes.getByType(ObjectType.ROOT);
                try {
                    while (iterator.hasNext()) {
                        String root = iterator.next();
                        dumpObjects(dao, root);
                    }
                } finally {
                    iterator.close();
                }

                // iterate over unrooted objects
                dumpObjects(dao, Constants.NO_ROOT);
            }

            private void dumpObjects(DAO dao, String root) {
                ObjectNode node = mapper.createObjectNode();
                forest.set(root, traverse(dao, root, node));
            }

            private ObjectNode traverse(DAO dao, String parent, ObjectNode parentNode) {
                Map<String, ObjectNode> folders = Maps.newHashMap();
                ResultIterator<Child> iterator = dao.children.getChildren(parent);
                try {
                    while (iterator.hasNext()) {
                        Child child = iterator.next();
                        LOGGER.debug("{} -> {}", parent, child);

                        ObjectNode node = mapper.createObjectNode();
                        node.put("type", child.objectType.name());

                        if (child.objectType == ObjectType.FILE) {
                            Content content = dao.objectProperties.getLatest(child.oid);
                            if (content != null) {
                                node.put("hash", content.hash);
                                node.put("size", content.size);
                                node.put("mtime", content.mtime);
                            }
                        }

                        if (child.objectType == ObjectType.FOLDER) {
                            folders.put(child.oid, node);
                        }

                        parentNode.set(child.name, node);
                    }
                } finally {
                    iterator.close();
                }

                // recurse into contained folders and mount points
                for (Map.Entry<String, ObjectNode> folder : folders.entrySet()) {
                    traverse(dao, folder.getKey(), folder.getValue());
                }

                // allow returns to be chained
                return parentNode;
            }
        }, Connection.TRANSACTION_READ_COMMITTED);

        Object serialized = mapper.treeToValue(forest, Object.class);
        TasksResource.printFormattedJson(serialized, queryParameters, mapper, outputWriter);
    }
}

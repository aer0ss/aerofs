package com.aerofs.polaris.logical;

import com.aerofs.baseline.admin.Command;
import com.aerofs.baseline.admin.Commands;
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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.Map;

@Singleton
public final class TreeCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeCommand.class);

    private final LogicalObjectStore objectStore;
    private final ObjectMapper mapper;

    @Inject
    public TreeCommand(LogicalObjectStore objectStore, ObjectMapper mapper) {
        this.objectStore = objectStore;
        this.mapper = mapper;
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter entityWriter) throws Exception {
        final String root = queryParameters.getFirst("root");
        final ObjectNode forest = mapper.createObjectNode();

        objectStore.inTransaction(new Transactional<Object>() {
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
                try (ResultIterator<Child> iterator = dao.children.getChildren(parent)) {
                    while (iterator.hasNext()) {
                        Child child = iterator.next();
                        LOGGER.debug("{} -> {}", parent, child);

                        ObjectNode node = mapper.createObjectNode();
                        node.put("type", child.getObjectType().name());

                        if (child.getObjectType() == ObjectType.FILE) {
                            Content content = dao.objectProperties.getLatest(child.getOid());
                            if (content != null) {
                                node.put("hash", content.getHash());
                                node.put("size", content.getSize());
                                node.put("mtime", content.getMtime());
                            }
                        }

                        if (child.getObjectType() == ObjectType.FOLDER) {
                            folders.put(child.getOid(), node);
                        }

                        parentNode.set(child.getName(), node);
                    }
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
        Commands.outputFormattedJson(mapper, entityWriter, queryParameters, serialized);
    }
}

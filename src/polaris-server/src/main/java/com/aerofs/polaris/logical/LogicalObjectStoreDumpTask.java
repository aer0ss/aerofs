package com.aerofs.polaris.logical;

import com.aerofs.baseline.Task;
import com.aerofs.baseline.admin.TasksResource;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.Child;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.util.List;

public final class LogicalObjectStoreDumpTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicalObjectStoreDumpTask.class);

    private final LogicalObjectStore logicalObjectStore;
    private final ObjectMapper mapper;

    public LogicalObjectStoreDumpTask(LogicalObjectStore logicalObjectStore, ObjectMapper mapper) {
        this.logicalObjectStore = logicalObjectStore;
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return "dump";
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter outputWriter) throws Exception {
        final String root = queryParameters.getFirst("root");
        final ObjectNode forest = mapper.createObjectNode();

        // FIXME (AG): don't do this in a single transaction
        logicalObjectStore.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                if (root != null) {
                    dumpRoot(conn, root);
                } else {
                    dumpObjectDatabase(conn);
                }

                return null;
            }

            private void dumpRoot(Handle conn, String root) {
                Children children = conn.attach(Children.class);
                forest.set(root, traverse(root, children));
            }

            private void dumpObjectDatabase(Handle conn) {
                // dao objects
                LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
                Children children = conn.attach(Children.class);
                ObjectTypes objectTypes = conn.attach(ObjectTypes.class);

                // iterate over rooted objects
                ResultIterator<String> iterator = objectTypes.getByType(ObjectType.ROOT);
                try {
                    while (iterator.hasNext()) {
                        String root = iterator.next();
                        forest.set(root, traverse(root, children));
                    }
                } finally {
                    iterator.close();
                }

                // iterate over the unrooted objects
                ObjectNode unrootedRoot = mapper.createObjectNode();
                forest.set(Constants.NO_ROOT, unrootedRoot);

                ResultIterator <LogicalObject> unrootedIterator = logicalObjects.getChildren(Constants.NO_ROOT);
                try {
                    while (unrootedIterator.hasNext()) {
                        LogicalObject unrooted = unrootedIterator.next();
                        unrootedRoot.set(unrooted.oid, traverse(unrooted.oid, children));
                    }
                } finally {
                    unrootedIterator.close();
                }
            }

            private ObjectNode traverse(String oid, Children children) {
                ObjectNode parent = mapper.createObjectNode();
                ArrayNode files = mapper.createArrayNode();

                List<String> folders = Lists.newArrayListWithCapacity(10);
                ResultIterator<Child> iterator = children.listChildren(oid);
                try {
                    while (iterator.hasNext()) {
                        Child child = iterator.next();
                        LOGGER.debug("{} -> {}", oid, child);

                        switch (child.objectType) {
                            case FILE:
                                files.add(child.oid);
                                break;
                            default:
                                folders.add(child.oid);
                        }
                    }
                } finally {
                    iterator.close();
                }

                // add a node called "files" only if the folder *has* files
                if (files.size() != 0) {
                    parent.set("files", files);
                }

                // recurse into contained folders and mount points
                for (String folder : folders) {
                    parent.set(folder, traverse(folder, children));
                }

                return parent;
            }
        });

        Object serialized = mapper.treeToValue(forest, Object.class);
        TasksResource.printFormattedJson(serialized, queryParameters, mapper, outputWriter);
    }
}

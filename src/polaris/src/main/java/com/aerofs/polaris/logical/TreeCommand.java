package com.aerofs.polaris.logical;

import com.aerofs.baseline.admin.Command;
import com.aerofs.baseline.admin.Commands;
import com.aerofs.baseline.db.TransactionIsolation;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.api.types.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.util.Map;

@Singleton
public final class TreeCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeCommand.class);

    private final ObjectStore store;
    private final ObjectMapper mapper;

    @Inject
    public TreeCommand(ObjectStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter entityWriter) throws Exception {
        ObjectNode forest = mapper.createObjectNode();

        String rootValue = queryParameters.getFirst("root");
        final OID root = rootValue == null ? null : new OID(rootValue);

        store.inTransaction(new StoreTransaction<Object>() {
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
                try (ResultIterator<UniqueID> iterator = dao.objectTypes.getByType(ObjectType.ROOT)) {
                    while (iterator.hasNext()) {
                        UniqueID root = iterator.next();
                        dumpObjects(dao, root);
                    }
                }

                // iterate over unrooted objects
                dumpObjects(dao, OID.TRASH);
            }

            private void dumpObjects(DAO dao, UniqueID root) {
                ObjectNode node = mapper.createObjectNode();
                forest.set(root.toStringFormal(), traverse(dao, root, node));
            }

            private ObjectNode traverse(DAO dao, UniqueID parent, ObjectNode parentNode) {
                Map<UniqueID, ObjectNode> folders = Maps.newHashMap();
                try (ResultIterator<Child> iterator = dao.children.getChildren(parent)) {
                    while (iterator.hasNext()) {
                        Child child = iterator.next();
                        LOGGER.debug("{} -> {}", parent, child);

                        ObjectNode node = mapper.createObjectNode();
                        node.put("type", child.objectType.name());

                        if (child.objectType == ObjectType.FILE) {
                            Content content = dao.objectProperties.getLatest(child.oid);
                            if (content != null) {
                                Preconditions.checkArgument(content.hash != null, "null content for o:%s v:%s", content.oid, content.version);
                                node.put("hash", PolarisUtilities.hexEncode(content.hash));
                                node.put("size", content.size);
                                node.put("mtime", content.mtime);
                            }
                        }

                        if (child.objectType == ObjectType.FOLDER) {
                            folders.put(child.oid, node);
                        }

                        parentNode.set(PolarisUtilities.stringFromUTF8Bytes(child.name),node);
                    }
                }

                // recurse into contained folders and mount points
                for (Map.Entry<UniqueID, ObjectNode> folder : folders.entrySet()) {
                    traverse(dao, folder.getKey(), folder.getValue());
                }

                // allow returns to be chained
                return parentNode;
            }
        }, TransactionIsolation.READ_COMMITTED);

        Object serialized = mapper.treeToValue(forest, Object.class);
        Commands.outputFormattedJson(mapper, entityWriter, queryParameters, serialized);
    }
}

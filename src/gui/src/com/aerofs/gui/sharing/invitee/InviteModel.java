/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.invitee;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * InviteModel is a collection of methods to provide backend support to the invite user dialog.
 */
public class InviteModel
{
    private static final Logger l = Loggers.getLogger(InviteModel.class);

    private final InjectableCfg                         _cfg;
    private final InjectableSPBlockingClientFactory     _spClientFactory;
    private final IRitualClientProvider                 _ritualProvider;
    private final ListeningExecutorService              _executor;

    private List<Invitee>                               _invitees = emptyList();

    // TODO (AT): In retrospect, it may be simpler to simply let the GUI keep track of the last
    //   query task instead of keeping track of it here. The advantage being that cancelling is
    //   guaranteed to be done on the GUI thread so we don't have to worry about concurrency. Also
    //   the GUI can ensure that the query task field is reset whenever it completes.
    private @Nullable ListenableFuture<List<Invitee>>   _lastQueryTask = null;

    public InviteModel(InjectableCfg cfg, InjectableSPBlockingClientFactory spClientFactory,
            IRitualClientProvider ritualProvider, ListeningExecutorService executor)
    {
        _cfg                = cfg;
        _spClientFactory    = spClientFactory;
        _ritualProvider     = ritualProvider;
        _executor           = executor;
    }

    public void load()
    {
        // TODO (AT): replace this to load from the real data source / SP.
        // uncomment the following line to test the UI with fake invitees.
        // _executor.submit(() -> _invitees = createFakeInvitees());
    }

    // this method is a misfit, but we need this on the invite dialog.
    public ListenableFuture<String> getLocalUserFirstName()
    {
        return _executor.submit(() -> _spClientFactory.create()
                .signInRemote()
                .getUserPreferences(_cfg.did().toPB())
                .getFirstName());
    }

    /**
     * the future will throw Exception if the callback was invoked after the query have gone stale.
     */
    public ListenableFuture<List<Invitee>> query(String query)
    {
        if (_lastQueryTask != null) {
            // N.B. we don't reset _lastQueryTask on completion. Therefore, we will keep a reference
            // of the last query task object until the GC collects the model.
            // This approach also means that we will invoke cancel on tasks that may have already
            // finished; this is okay.
            _lastQueryTask.cancel(true);
            _lastQueryTask = null;
        }

        if (isBlank(query) || _invitees.isEmpty()) {
            return immediateFuture(emptyList());
        } else {
            _lastQueryTask = _executor.submit(() -> queryImpl(query));
            return _lastQueryTask;
        }
    }

    // TODO (AT): improve search algorithm to use a rank-based approach
    private List<Invitee> queryImpl(String query)
    {
        return _invitees.stream()
                .filter(invitee -> invitee._shortText.toLowerCase()
                        .startsWith(query.toLowerCase()))
                .limit(6)
                .collect(toList());
    }

    public ListenableFuture<Void> invite(Path path, List<Invitee> invitees, Permissions permissions,
            String note, boolean suppressSharedFolderRulesWarnings)
    {
        return _executor.submit(() -> {
            // TODO (AT): support sharing with groups
            List<PBSubjectPermissions> pbsps = invitees.stream()
                    .filter(invitee -> invitee._type == InviteeType.USER)
                    .map(invitee -> new SubjectPermissions((UserID)invitee._value, permissions))
                    .map(SubjectPermissions::toPB)
                    .collect(toList());
            _ritualProvider.getBlockingClient().shareFolder(path.toPB(), pbsps, note,
                    suppressSharedFolderRulesWarnings);
            return null;
        });
    }

    public Invitee createInvitee(String text)
    {
        try {
            if (!Util.isValidEmailAddress(text)) {
                return createInvalidInvitee(text);
            }
            return createUserInvitee(UserID.fromExternal(text), "", "");
        } catch (Exception e) {
            return createInvalidInvitee(text);
        }
    }

    private Invitee createUserInvitee(UserID userID, String firstname, String lastname)
    {
        String fullname = (firstname + " " + lastname).trim();
        String longText = isBlank(fullname)
                ? userID.getString()
                : fullname + " (" + userID.getString() + ")";
        return new Invitee(InviteeType.USER, userID,
                isBlank(fullname) ? userID.getString() : fullname, longText);
    }

    private Invitee createGroupInvitee(int groupID, String name)
    {
        return new Invitee(InviteeType.GROUP, groupID, name, name);
    }

    private Invitee createInvalidInvitee(String text)
    {
        text = text.trim();
        return new Invitee(InviteeType.INVALID, text, text, text);
    }

    public enum InviteeType
    {
        USER, GROUP, INVALID
    }

    public class Invitee
    {
        // TODO (AT): not a fan of using _type & untyped _value, suggestions welcomed
        public final InviteeType _type;
        public final Object _value;
        public final String _shortText;
        public final String _longText;

        public Invitee(InviteeType type, Object value, String shortText, String longText)
        {
            _type = type;
            _value = value;
            _shortText = shortText;
            _longText = longText;
        }
    }

    // fake data used to test the UI
    private List<Invitee> createFakeInvitees()
    {
        List<Invitee> invitees = newArrayList();

        String hacks = "Alex\tTsay\n" +
                "Allen\tGeorge\n" +
                "Drew\tFisher\n" +
                "Hugues\tBruant\n" +
                "Erik\tMall\n" +
                "Jon\tPile\n" +
                "Jonathan \tGray\n" +
                "Matt\tPillar\n" +
                "Weihan\tWang\n" +
                "Yuri\tSagalov\n" +
                "Suthan\tNandakumar\n" +
                "Karen\tRustad\n" +
                "John\tGabaix\n" +
                "Callie\tStrawn\n" +
                "Chris\tDudley\n" +
                "Blake\tSwineford\n" +
                "Adam\tPoliak\n" +
                "Abhishek\tSharma\n" +
                "Rudy\tDai\n" +
                "Abra\tKadabra\n" +
                "Alakazam\t \n";

        for(String line : hacks.split("\\n")) {
            String[] tokens = line.split("\\t");
            invitees.add(createUserInvitee(UserID.fromInternal(tokens[0].toLowerCase() + "@aerofs.com"),
                    tokens[0], tokens[1]));
        }

        invitees.add(createGroupInvitee(0, "Team AeroFS"));
        invitees.add(createGroupInvitee(1, "Sales"));
        invitees.add(createGroupInvitee(2, "Marketing"));
        invitees.add(createGroupInvitee(3, "Operation"));
        invitees.add(createGroupInvitee(4, "Engineering"));

        return invitees;
    }
}

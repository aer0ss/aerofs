package com.aerofs.servlets.lib;

import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class ThreadLocalSFNotifications {
    private ThreadLocal<List<SFNotification>> notifs = new ThreadLocal<>();

    public void begin() {
        notifs.set(Lists.newArrayList());
    }

    // no-op if already finished
    public void clear() {
        notifs.remove();
    }

    public List<SFNotification> get() {
        Preconditions.checkState(notifs.get() != null, "did not begin sf notifications queue before getting notif");
        return compact();
    }

    // TODO (RD) also make this remove duplicate notifs, should a LEAVE also emcompass a CHANGE?
    private List<SFNotification> compact() {
        // making sure spurious notifications don't get sent
        List<SFNotification> messages = notifs.get();
        List<SFNotification> removed = messages.stream()
                .filter(notif -> notif.msg == SFNotificationMessage.LEAVE)
                .map(notif -> new SFNotification(notif.uid, notif.sid, SFNotificationMessage.JOIN))
                .collect(Collectors.toList());
        removed.forEach(messages::remove);
        return messages;
    }

    public void addNotif(UserID user, SID store, SFNotificationMessage messsage) {
        Preconditions.checkState(notifs.get() != null, "did not begin sf notifications queue before adding notif");
        notifs.get().add(new SFNotification(user, store, messsage));
    }

    public enum SFNotificationMessage {
        // send notification to insert store under user root store
        JOIN("j"),
        // send notification to remove store from under user root store
        LEAVE("l"),
        // ACLs may/may not have updated for this user
        CHANGE("c");

        private final String text;

        SFNotificationMessage(String s) {
            text = s;
        }

        @Override
        public String toString() {
            return text;
        }
    }


    public static class SFNotification {
        public final UserID uid;
        public final SID sid;
        public final SFNotificationMessage msg;

        public SFNotification(UserID user, SID store, SFNotificationMessage message) {
            uid = user;
            sid = store;
            msg = message;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SFNotification other = (SFNotification) o;
            return Objects.equal(this.uid, other.uid) &&
                    Objects.equal(this.sid, other.sid) &&
                    this.msg == other.msg;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(uid, sid, msg);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("user", uid)
                    .add("store", sid)
                    .add("message", msg)
                    .toString();
        }
    }
}


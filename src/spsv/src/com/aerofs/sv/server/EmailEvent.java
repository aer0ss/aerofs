package com.aerofs.sv.server;

import com.aerofs.sv.common.Event;

public class EmailEvent
{

    public final String _email;
    public final Event _event;
    public final String _desc;
    public final String _category;
    public final Long _timestamp;

    /**
     *
     * @param email the email address in question
     * @param event the event (delivered, unsubscribe, etc..., according to Sendgrid's API)
     * @param desc an optional description of the event (see SendGrid's API)
     * @param category the category the email was sent under
     * @param timestamp the timestamp of the event
     */
    EmailEvent(String email, Event event, String desc, String category, Long timestamp)
    {
        _email = email;
        _event = event;
        _desc = desc;
        _category = category;
        _timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;

        EmailEvent ee = (EmailEvent)o;
        return _email.equals(ee._email) && _event.equals(ee._event) && _desc.equals(ee._desc) &&
                _category.equals(ee._category) && _timestamp.equals(ee._timestamp);
    }

    @Override
    public int hashCode()
    {
        return _email.hashCode() + _event.hashCode() + _desc.hashCode() +
                _category.hashCode() + _timestamp.hashCode();
    }
}

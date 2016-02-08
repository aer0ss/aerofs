CREATE INDEX convo_members_cid ON convo_members (convo_id); -- get all members for a convo
CREATE INDEX convo_members_uid ON convo_members (user_id);  -- get all convos for a user

CREATE INDEX convo_sid ON convos (sid); -- get convo for file update, get all convos with sids

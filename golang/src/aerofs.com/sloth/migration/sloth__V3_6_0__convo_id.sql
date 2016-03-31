/** Conversation ID's can now be a maximum of 33 chars
 * This is to allow a corresponding file OID to be deduced
 * from a given convoId by extracting the latter 32 chars.
 */

ALTER TABLE convos MODIFY COLUMN id VARCHAR(33);
ALTER TABLE convo_members MODIFY COLUMN convo_id VARCHAR(33);

/* All Bot messages now use a data payload. Update previous
 * bot messages to the new format
 */
UPDATE messages SET body=concat("{\"body\":\"", body, "\"}"), is_data=1 WHERE from_id IN (select id from bots);

/* Distinguish different type of bots so we can handle different
 * incoming paylods.
 */
ALTER TABLE bots ADD COLUMN type TINYINT NOT NULL DEFAULT 1;

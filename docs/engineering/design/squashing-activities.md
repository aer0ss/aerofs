# Recent activities feed

As part of project pages for Project Breyttafoss, we want to build a more human-friendly activity feed. (This would be useful not just for project pages, but for recent activities in general.)

## Example feed items

What sort of feed items are we trying to be able to generate?

 - "Jane made 4 changes to design.psd. a minute ago"
 - "Jane renamed notes.txt to design_notes.txt. 12 minutes ago"
 - "Jane moved design.psd and three other files into folder src/resources. 13 minutes ago"
 - "Jane renamed blah.psd to design.psd. 15 minutes ago"
 - "Eva added files report.tex and report.pdf. 2 hours ago"
 - "Amy shared Manhattan Project with Gordon Korman and Eva Vorpal. 16 hours ago"
 - "Jane made many changes to blah.psd and two other files. 1 day ago"
 - "Mary-Rose moved design/blah.psd and design/notes.txt into the main shared folder. 2 days ago"
 - "Mary-Rose deleted design/old-stuff/oldlogo.ai and 17 other files. 2 days ago"

(Filenames are links to those files within a web directory viewer, or web editor if available. "X other files" expands via tooltip or similar to show the names and links to those files.)

## Recent activities squashing protocol

To make the activity feed useful and not super noisy, we need to be able to squash related events into a single notification in the feed, dynamically over time, batched in reverse chronological order. This is most efficiently done on the backend side of things.

Within a moving window of time, going back in time from most recent to less recent:

 - If two events are from the same device (or user, but device comparison is easier), and are the same event type, they get squashed into a compound event.

 - Compound events include all the information for the events they contain, so that the view later can show details.

 - The compound event shows the timestamp of its newest contained event to the user. It uses the timestamp of its oldest contained event when determining if earlier events ought to be squashed into it. (So with a 1 hour window, several hours of changes can get squashed together, if there's enough of them close enough together.)

 - Events get squashed by SOID. The filename displayed is what the file was called at the most recent event within the squashing. (So edit events before and after a filename change events may be combined.)

 - Default squashing time window length is suggested to be one hour. Could be different for different action types (e.g. 12 hours for new shares).

We could decide that certain types of events break up squashing even if similar events are close enough to each other in time. E.g. Jane modifies a file, renames the file, and modifies the renamed file. Even if the two modifications are within an hour of each other, we could decide renames are important enough for this to display as three events rather than two.

The filename links go to where the file is currently, not where it was when the event was created. (So file moves don't break things.) If the file has been subsequently deleted, there is no link (until we have file recovery/versioning via web, at least).

The specific wording of the feed statement, including the cutoff for using "many" instead of a specific number, is determined on the frontend.

Future versions may have special behavior for events inside a common subfolder. For now, let's decide to not handle squashing on folders (e.g. "Jane made six modifications to files inside folder Design Mockups").

Future versions might also pass rules for squashing or a level of squashing aggressiveness as a parameter. This would allow e.g. mobile clients to request a much more terse activity log than desktop clients. This will not be part of version one, though.
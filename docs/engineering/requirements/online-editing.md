# Live Document Editing

    v0.1 2015/03/24 jP Initial functional spec


# Background and motivation

We would like to give users an online-editing option for cases when they do not
have the physical document locally available. At the same time, we would like
to offer users the ability to collaboratively edit a document in real time -
viewing changes to a common document as they are made by a peer.

Open-Xchange offers a wide array of product features around a document and mail
collaboration suite.

See http://oxpedia.org/wiki/index.php?title=AppSuite:Architecture_Overview

For Live Editing, we only need a small subset:
 - Documents
 - Spreadsheets
 - Viewer (tbd)

Specifically, features like Contacts, Mail, Calendar etc., are not currently
planned for integration.


# Functional Specification

## Browsing content

### Online browsing

A logged-in AeroFS user is browsing their own content in the *My Files* view.
Their content includes their own files as well as the shared folders to which
they belong.

They find a relevant Doc file; the options presented are **View**,  **Edit**,
and **Download**. Note that **Edit** should not be presented if the user does
not have editor privilege for the given file.

The user selects **View** : a readonly view of the document is shown in the
browser. It may pop up in a new window.

The user selects **Download** : a copy of the document downloads in the browser
(as today).

The user selects **Edit** : an editable view of the document is shown in the
browser. See **Document Editing**.


### Offline browsing

The user is looking at their own content in the system-native file browser
(Finder, Explorer, Nautilus, etc.). They right-click a file and choose "_Edit
Online_" from the context menu. This loads an editable view of the document in
the default browser.


### Supported Types

**TODO**: Get the full list from OX.

The View/Edit buttons are made visible (or hidden) based on the extension of
the underlying file.

At minimum, the following document types are available for editing:

 - Word : `.doc`, `.docx`

 - Excel : `.xls`, `.xlsx`

 - Text : `.txt` (true, right?)


## Document Editing


### Storage

As the user makes changes, the browser regularly 'saves' the document. These
saves are synced directly via the API to the AeroFS store for the user. A
connected device that syncs the AeroFS folder will regularly see the latest
version of the document.


### Concurrent online access

Another user selects **Edit** on the same file. They also see an editable
in-browser copy of the document. A popup alerts them they are not the
lock-holder - they can see changes as they are made, but they will not be able
to edit the document until they acquire  the editing lock.


### Concurrent access from the filesystem

Another user edits the document on their own filesystem. Each new version of
the document that is uploaded to the storage agent will result in a
corresponding update of the online view.


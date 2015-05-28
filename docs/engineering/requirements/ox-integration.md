# Synopsis
This document outlines the requirements on integrating AeroFS with OpenXchange
AppSuite to meet the requirements for the online-editing feature.

# Motivation
We would like to offer users a feature so that they may view and edit their
documents collaboratively in real time regardless of the location of the
documents.

# Requirements
The users should be able to:

* View and edit a document when they do not have the document locally available.
* View and edit a document while other users are viewing and editing the same
document.
* Observe the changes they've made if they open the document in another
application[^note1].
* Observe the changes made by other users to the same document in real time.

[^note1]: Further changes do not need to be observed in the other application
while the other application is running.

# User Workflow
* From AeroFS, the users select a document and chooses to edit the selected
document.
* The corresponding document editor from Open-Xchange opens up in a new browser
tab with the content of the document already loaded.
* The user views and edits the document in the browser tab.
* The user closes the browser tab when they are done.

# Supported Document Types
For the time being:

* Word Documents: `.doc, .docx`
* Excel Spreadsheets: `.xls, .xlsx`
* Text File: `.txt`

Note that format compatibility with Microsoft Office is mission-critical.

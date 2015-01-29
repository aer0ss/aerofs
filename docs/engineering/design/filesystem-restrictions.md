Dealing with filesystems restrictions
=====================================

## Problem

Different filesystems have different capabilities and restrictions.
Some Operating Systems add extra restrictions on top of that at the
API layer.

In the past AeroFS approached this problem by catering to the lowest
common denominator, i.e. case-insensitivity, and assuming otherwise
sensible file names.

In practice this approached was plagued by a number of significant
technical issues:

  - files with Win32-invalid names created in OSX or Windows would
    cause persistent no-sync on Windows machines until they were
    renamed.
  - files differing only by case could be created on Linux and would
    completely break the Linker/Scanner in subtle, hard to diagnose
    and largely unfixable ways.

In terms of UX this implied:

  - homogeneous clients were not just limited by what they could deal
    with but by what the lowest common denominator the whole system
    catered to
  - files created on one device could cause persistent no-sync or
    crash loops on other device, in other words a Denial of Service
  - potentially unsyncable files could be seen in the sender but not
    the receiver and the UI for that is not easily accessible


## Goals

AeroFS aims to be as transparent as possible and adapt to whatever
workflow the users are using. To achieve that the logical filesystem
should be at least as permissive as the most permissive supported
physical filesystem.

This will offer the best experience on sets of homogenous devices:
any file being created on one will be syncable on all others. One
important benefit of this approach is that Linux users get full
case-sensitivity support.

A further goal is graceful degradation on more limited filesystems.
The core syncing algorithm should always works regardless of the
limitations of the underlying physical storage. Note that these
considerations are only relevant to LinkedStorage as BlockStorage,
whether on local disk, S3 or any other backend we may add in the
future does not run into these issues.


## Design choices

The AeroFS virtual filesystem accepts valid unicode strings as filenames.
It is case-sensitive and normalization-sensitive and the only unicode
character it expressely forbids within a filename is the forward slash '/'.

Sensible Operating Systems should enforce the use of Unicode for
filenames (UTF-8 being the obvious choice). For systems where this
is not true the JVM may do the appropriate conversion for us, or it
may not. In any case these systems are NOT SUPPORTED.

Use Unicode or GTFO.


## Implementation


### Non-Representable Objects

We introduce the concept of Non-Representable Object (NRO). An object
(file or folder) is Non-Representable if it cannot be created on the
underlying filesystems.

We distinguish two types of NROs:

  - inherently non-representable: filename including illegal characters,
    e.g. `foo|bar.baz` on Windows
  - contextually non-representable: filename conflicts with another
    object, e.g. `foo` and `Foo` on a case-insensitive filesystem.

We refer to these subsets as INRO and CNRO respectively.


### Database schema

To keep track of NROs, a new table is added to the core DB:

Column  |  Type  | comment
--------|--------|-------------------------------------------------------
`nro_s` | SIndex |
`nro_o` | OID    | OID of the NRO
`nro_c` | OID    | OID of the object occupying the physical path, if any

The presence of an SOID in this table implies that the object is
currently non-representable, however the reverse is not true.

When a path can be unambiguously determined to be non-representable
by string checks alone, the database is bypassed as an optimization.

The content of the `nro_c` column is null for INROs. For CNROs it points
to the sibling currently occupying the physical path. Maintaining this
link information allows transparent selection of a new "winner" among
the set of CNROs when the conflicting object is deleted or renamed.


### NRO detection

NROs can be detected in two ways. First a quick platform-dependent check
on the virtual path can be done to detect illegal characters and similar
limitations. Contrary to popular belief this is not merely an optimization.

Relying on the filesystem to help you is a sure way to shoot yourself in
the foot. Make no mistake, when building a file syncing software the
filesystem is your worst enemy. If you underestimate its ability to fuck
with you and let your guard down just one nanosecond you may not live to
regret it.

The filesystem will sometimes happily let you write files, only to be reject
your read requests later on because it somehow normalized the names you gave
it and omitted to tell you.

Examples of such stupid behaviors we actually ran into:

  - OSX will silently convert NFC to NFD
  - Windows may in some cases remove trailing spaces and dots.

Some of these checks are OS-specific (e.g. forbidden characters on Windows
and NFD normalization on OSX), others are filesystem-specific (case-insensitivity).
It is important that OS-specific checks be *less* strict than filesystem-specific
ones.

Every time the daemon starts it will probe filesystems capabilities
(e.g. case-sensitivity) for each physical root and use this information
to make filesystem-specific checks. We are operating under the assumption
that a physical root is contained in a single volume/partition. Mount
points and other crazyness will break other parts of the daemon and thus
need not be considered for this feature.

For bulletproof detection some physical operations are needed (checking
for file existence through the OS API, comparing inode numbers).

Finally, the last line of defense is to attempt the real operation (create,
move) and react to exceptions by treating the object as non-representable.


### NRO storage

NROs are stored as `<AUXROOT>/nro/<SOID>`

Representable children of non-representable folders are stored with their
regular name under the NRO folder. This keeps the implementation simple
and efficient, especially when moving large hierarchies when a folder
sees its representability change.

For instance the following hierarchy on Linux:

    AeroFS/
        foo/
            bar
            Bar
        Foo./
            bar/
                qux
                QUX

Could be stored as follows on Windows:

    AeroFS/
        foo/
            Bar
    .aux.aerofs.<sid>/
        nro/
            <soid:"Foo.">/
                bar/
                    qux
            <soid:"bar">
            <soid:"QUX">


### Representability changes

An important property of this design is that the virtual filesystem remains
blissfully oblivious to the issue of representability.

Whenever an object is renamed or deleted, the physical layer will take the
appropriate action(s) to update its representability information and, if
applicable, that of the object(s) it conflicted with.

In the previous example this means that:

  - deleting `foo/Bar` would cause `foo/bar` to appear on Windows
  - renaming `Foo.` to `Foo.d` would cause it to appear on Windows


## User Interface

The ancient "Why aren't my files synced" dialog will get a much needed
makeover. Instead of scanning the filesystem for files that could
potentially not show up on remote peers it will list all local NROs.
Similar to what is done for revision history this listing is obtained
by scanning the nro auxilliary folder.

Requirements for this new dialog:

  - listing NROs
  - explaining cause of non-representability as accurately as possible
    and pointing to an FAQ when detection heuristics fail
  - viewing read-only copies of NROs
  - renaming/deleting NROs

In addition to this refreshed dialog, the UI should notify the user of
the existence of NROs in a manner similar to what is done for conflicts:

  - Show number of NROs
  - Add colored dot to tray icon when new NRO appears


## Appendix A: Known restrictions

### Windows

  - case-insensitive at Win32-layer. NTFS is actually case-sensitive but
    one must use the POSIX API to operate case-sensitively and most apps
    (notably Explorer) don't. Case-preserving.
  - default to NFC, normalization-preserving
  - forbidden characters:
     * ASCII `NUL`, i.e. byte `0x00`
     * ASCII control characters, i.e. bytes `0x01` to `0x1F`
     * `<`
     * `>`
     * `:`
     * `"`
     * `/`
     * `\`
     * `|`
     * `?`
     * `*`


NB: MSDN also mentions a bunch of patterns that are problematic because the shell, Explorer and
most Windows application cannot deal with them properly, however they are supported by the underlying
filesystem and can actually be manipulated by the Win32 API when using the `\\?\` prefix, therefore
we should be ready to handle them.

[Reference](http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx)

### OSX

  - case-insensitive by default but HFS+ can be configured to be case-sensitive
  - paths are normalized to NFD by the OSX filesystem API<br/>
    Yes, that's right, OSX **fails to be normalization-preserving**, something even Windows does.<br/>
    Adding insult to injury, the morons at Apple had to pick NFD even though all other OSes default to NFC.<br/>
    Worse yet, they use a **non-standard** [variant of NFD](https://developer.apple.com/library/mac/qa/qa1173/_index.html)
    that is only partially decomposed.
  - forbidden characters:
      * `/`


### Linux

  - case-sensitive by default but some filesystems may be case-insensitive (e.g. FAT)
  - default to NFC, normalization-preserving
  - forbidden characters:
      * ACII `NUL`, i.e. byte `0x00`
      * `/`

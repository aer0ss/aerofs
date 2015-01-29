The linker is the component that monitors local filesystem changes and performs necessary updates in the core, so that those changes are reflected in AeroFS.

## Comparing file paths vs i-node numbers

What if a file or a folder is moved? We don't want to view it as deletion + recreation, which would force other peers to redownload the entire content of that file or folder. This can be overcome by computing content hashing, but we'd avoid hashing as much as possible for performance. Therefore, we need to use i-node numbers as a lifetime identifier of an object, so that object movement can be tracked reliably.

A problem with i-node numbers is that some filesystems doesn't support persistent numbers, for example, FAT on Linux. This can be worked around by the technique described in the next section.

## An invariant

Files already added to beta and omega sets should not be added to gamma. (See the whiteboard picture saved in the same folder as this file.) Otherwise, the files will be incorrectly deleted.

## Editor behavior

Create a temp file, delete the original, rename temp file.

Solution: a ignore list + update the original OID instead of replacement with a new OID on FID change

## Annoying corner cases

If when AeroFS is not running, file A is moved to B, and a new file is created at A. The linker will assign the new A's content to the old A, and then created B. The desired behavior would be moving A to B and create a new A.

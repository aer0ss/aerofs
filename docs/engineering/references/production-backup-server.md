# Production Backup Server

*Editor's note: the following comments are from Drew. There is much to be
improved here; in this version of this document I'm simply writing down what
we know for reference.*

## High Level

The goal is to backup the potentially-sensitive or deployment-specific data
that we only have one copy of elsewhere (e.g. puppet hieradata, ssl keys,
joan's root CA key/cert) and no other disaster recovery model for. (So we don't
need this for RDS databases, or other things we can reproduce from the repo or
just mint anew with no trouble if we lose them.)

The implementation is vpc:backup has an ssh key whose public half is in
root@puppet and root@joan's authorized_keys. It runs a script from a cron job
in root's crontab which rsyncs several folders from each of the hosts to be
backed up. It does this with hardlinks, so you can see what the folders looked
like on any particular date, and prune backups simply by deleting whole dated
folders.

"sudo crontab -l" will name the backup script(s), and from there it's pretty
readable. That (and the data it produces) are the only things of interest on
the machine.

There's some mild intelligence in the backup script for retention policy --
something like keep every day in the past week, the first of the week for the
past month, the first of the month for the past year. The scripts are currently
not stored in git.

## Access

Currently only has Matt has access to this machine. In the future it should be
properly puppetized or similar.

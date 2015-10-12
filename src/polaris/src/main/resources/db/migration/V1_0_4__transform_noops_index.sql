DROP INDEX `object_transforms_index` ON `transforms`;

CREATE INDEX `object_transforms_index` ON `transforms` (`oid`, `child_oid`);

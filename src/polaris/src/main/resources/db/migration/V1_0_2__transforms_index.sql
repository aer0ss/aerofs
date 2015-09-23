DROP INDEX `store_index` on `objects`;

CREATE INDEX `object_transforms_index` ON `transforms` (`oid`, `transform_type`);

DROP INDEX `store_max_logical_timestamp_index` ON `store_max_logical_timestamp`;

DROP INDEX `store_notified_logical_timestamp_index` ON `store_notified_logical_timestamp`;

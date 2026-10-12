-- Step 1: editable and deletable expenses and settlements, and optimistic locking.
--
-- * version     : JPA @Version. Every change increments it; an update based on an older version is rejected
--                 (409) instead of silently overwriting someone else's edit.
-- * deleted_at/by: soft delete. Deleted rows drop out of lists and balances but can be shown and restored;
--                 nothing is ever physically removed, so history stays auditable.
-- * updated_at/by: who last edited an expense, and when.

ALTER TABLE expense ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE expense ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE expense ADD COLUMN updated_by BIGINT;
ALTER TABLE expense ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE expense ADD COLUMN deleted_by BIGINT;
ALTER TABLE expense ADD CONSTRAINT fk_expense_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (id);
ALTER TABLE expense ADD CONSTRAINT fk_expense_deleted_by FOREIGN KEY (deleted_by) REFERENCES app_user (id);

ALTER TABLE settlement ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE settlement ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE settlement ADD COLUMN deleted_by BIGINT;
ALTER TABLE settlement ADD CONSTRAINT fk_settlement_deleted_by FOREIGN KEY (deleted_by) REFERENCES app_user (id);

ALTER TABLE expense_group ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

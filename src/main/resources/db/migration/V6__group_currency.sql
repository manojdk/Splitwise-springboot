-- Step 6: every group has one currency (existing groups are INR). Group expenses and payments use it.

ALTER TABLE expense_group ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR';

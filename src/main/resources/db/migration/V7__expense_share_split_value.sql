-- Frontend step 3: keep what the user typed for each share (the percentage, number of shares, exact amount or
-- adjustment), so an expense can be edited in the terms it was entered. NULL for EQUAL splits, for an
-- adjustment left empty (= 0), and for expenses created before this column existed.

ALTER TABLE expense_share ADD COLUMN split_value NUMERIC(19, 4);

-- Step 3: expense category and free-text notes. Existing expenses become GENERAL.

ALTER TABLE expense ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE expense ADD COLUMN notes VARCHAR(1000);
ALTER TABLE expense ADD CONSTRAINT ck_expense_category CHECK (category IN (
    'GENERAL', 'FOOD_AND_DRINK', 'GROCERIES', 'RENT', 'UTILITIES', 'TRANSPORT', 'TRAVEL',
    'ENTERTAINMENT', 'SHOPPING', 'HEALTH'));

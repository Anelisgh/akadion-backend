-- V2: Alterare coloane cursuri si constrangere unicitate saptamani

ALTER TABLE cursuri ALTER COLUMN data_inceput DROP NOT NULL;
ALTER TABLE cursuri ALTER COLUMN data_sfarsit DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_saptamani_curs_nr'
    ) THEN
        ALTER TABLE saptamani
            ADD CONSTRAINT uk_saptamani_curs_nr
            UNIQUE (id_curs, nr_saptamana);
    END IF;
END $$;

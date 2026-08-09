DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM parcursuri
        GROUP BY id_user_curs, id_saptamana
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Nu se poate adauga constrangerea uk_parcursuri_user_curs_saptamana deoarece exista duplicate in parcursuri pentru (id_user_curs, id_saptamana).';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_parcursuri_user_curs_saptamana'
    ) THEN
        ALTER TABLE parcursuri
            ADD CONSTRAINT uk_parcursuri_user_curs_saptamana
            UNIQUE (id_user_curs, id_saptamana);
    END IF;
END $$;

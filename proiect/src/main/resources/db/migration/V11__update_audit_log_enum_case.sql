-- Update any existing lowercase enum values to uppercase to match the Java enums
UPDATE audit_log
SET nume_tabel = UPPER(nume_tabel),
    operatie = UPPER(operatie);

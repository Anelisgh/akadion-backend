ALTER TABLE documente ADD COLUMN hash_continut VARCHAR(64);
CREATE UNIQUE INDEX uq_documente_hash_saptamana ON documente (id_saptamana, hash_continut) WHERE activ = true;

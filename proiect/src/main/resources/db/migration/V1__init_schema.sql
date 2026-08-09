-- V1: Schema initiala Akadion
-- Seed-ul pentru ROL si STARE_CONT este gestionat de DataSeeder.java (CommandLineRunner)
-- Bootstrap-ul primului admin: vezi scripts/bootstrap-admin.sql

-- Tabelul ROL
-- Valori seed: ADMIN, PROFESOR, STUDENT (inserate de DataSeeder)
CREATE TABLE IF NOT EXISTS roluri (
    id BIGSERIAL PRIMARY KEY,
    denumire VARCHAR(50) NOT NULL,
    CONSTRAINT uk_roluri_denumire UNIQUE (denumire)
);

-- Tabelul STARE_CONT
-- Valori seed: INCOMPLET, PENDING, ACTIV, INACTIV, RESPINS (inserate de DataSeeder)
CREATE TABLE IF NOT EXISTS stari_cont (
    id BIGSERIAL PRIMARY KEY,
    denumire VARCHAR(20) NOT NULL,
    CONSTRAINT uk_stari_cont_denumire UNIQUE (denumire)
);

-- Tabelul APP_USER
-- ⚠️ 'USER' este cuvânt rezervat în PostgreSQL — se foloseste 'app_user'
-- ID_KEYCLOAK este primit din Keycloak de la register si e NOT NULL + UNIQUE
-- ROL, NUME, PRENUME sunt nullable in prima faza (stare cont: INCOMPLET)
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    id_keycloak VARCHAR(36) NOT NULL,
    mail VARCHAR(100) NOT NULL,
    nume VARCHAR(100) NULL,
    prenume VARCHAR(100) NULL,
    facultate VARCHAR(100) NULL,
    id_stare_cont BIGINT NOT NULL,
    id_rol BIGINT NULL,
    nr_respingeri INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_app_user_id_keycloak UNIQUE (id_keycloak),
    CONSTRAINT uk_app_user_mail UNIQUE (mail),
    CONSTRAINT fk_app_user_stare_cont FOREIGN KEY (id_stare_cont) REFERENCES stari_cont (id),
    CONSTRAINT fk_app_user_rol FOREIGN KEY (id_rol) REFERENCES roluri (id)
);

CREATE INDEX IF NOT EXISTS idx_app_user_rol ON app_user (id_rol);
CREATE INDEX IF NOT EXISTS idx_app_user_stare_cont ON app_user (id_stare_cont);
CREATE INDEX IF NOT EXISTS idx_app_user_mail ON app_user (mail);
CREATE INDEX IF NOT EXISTS idx_app_user_id_keycloak ON app_user (id_keycloak);

-- Tabelul CURSURI
CREATE TABLE IF NOT EXISTS cursuri (
    id BIGSERIAL PRIMARY KEY,
    id_profesor BIGINT NOT NULL,
    denumire VARCHAR(150) NOT NULL,
    descriere VARCHAR(1000) NULL,
    data_inceput DATE NOT NULL,
    data_sfarsit DATE NOT NULL,
    activ BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_cursuri_profesor FOREIGN KEY (id_profesor) REFERENCES app_user (id)
);

CREATE INDEX IF NOT EXISTS idx_cursuri_profesor ON cursuri (id_profesor);

-- Tabelul USER_CURSURI (inscrieri studenti la cursuri)
CREATE TABLE IF NOT EXISTS user_cursuri (
    id BIGSERIAL PRIMARY KEY,
    id_student BIGINT NOT NULL,
    id_curs BIGINT NOT NULL,
    activ BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_user_cursuri_student_curs UNIQUE (id_student, id_curs),
    CONSTRAINT fk_user_cursuri_student FOREIGN KEY (id_student) REFERENCES app_user (id),
    CONSTRAINT fk_user_cursuri_curs FOREIGN KEY (id_curs) REFERENCES cursuri (id)
);

CREATE INDEX IF NOT EXISTS idx_user_cursuri_student ON user_cursuri (id_student);
CREATE INDEX IF NOT EXISTS idx_user_cursuri_curs ON user_cursuri (id_curs);

-- Tabelul SAPTAMANI
CREATE TABLE IF NOT EXISTS saptamani (
    id BIGSERIAL PRIMARY KEY,
    id_curs BIGINT NOT NULL,
    nr_saptamana INTEGER NOT NULL,
    descriere VARCHAR(500) NULL,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_saptamani_curs FOREIGN KEY (id_curs) REFERENCES cursuri (id)
);

CREATE INDEX IF NOT EXISTS idx_saptamani_curs ON saptamani (id_curs);

-- Tabelul PARCURSURI (progresul saptamanal al studentilor)
CREATE TABLE IF NOT EXISTS parcursuri (
    id BIGSERIAL PRIMARY KEY,
    id_user_curs BIGINT NOT NULL,
    id_saptamana BIGINT NOT NULL,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_parcursuri_user_curs FOREIGN KEY (id_user_curs) REFERENCES user_cursuri (id),
    CONSTRAINT fk_parcursuri_saptamana FOREIGN KEY (id_saptamana) REFERENCES saptamani (id)
);

CREATE INDEX IF NOT EXISTS idx_parcursuri_user_curs ON parcursuri (id_user_curs);
CREATE INDEX IF NOT EXISTS idx_parcursuri_saptamana ON parcursuri (id_saptamana);

-- Tabelul DOCUMENTE
CREATE TABLE IF NOT EXISTS documente (
    id BIGSERIAL PRIMARY KEY,
    id_saptamana BIGINT NOT NULL,
    titlu VARCHAR(255) NOT NULL,
    path_minio VARCHAR(512) NOT NULL,
    status_index VARCHAR(20) NOT NULL,
    activ BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_documente_saptamana FOREIGN KEY (id_saptamana) REFERENCES saptamani (id)
);

CREATE INDEX IF NOT EXISTS idx_documente_saptamana ON documente (id_saptamana);

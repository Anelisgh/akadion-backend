package com.example.akadion.common.entity;

// Reflectă valorile din tabela `roluri` (coloana denumire rămâne String în DB, neschimbată).
// Folosit doar în cod Java, la comparații, ca să evităm typo-uri în literalii de rol.
public enum NumeRol {
    ADMIN,
    PROFESOR,
    STUDENT
}

package com.example.akadion.common.entity;

// Reflectă valorile din tabela `stari_cont` (coloana denumire rămâne String în DB, neschimbată).
// Folosit doar în cod Java, la comparații, ca să evităm typo-uri în literalii de stare.
public enum NumeStareCont {
    ACTIV,
    PENDING,
    RESPINS,
    INACTIV,
    INCOMPLET
}

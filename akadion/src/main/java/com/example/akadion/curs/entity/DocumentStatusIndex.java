package com.example.akadion.curs.entity;

public enum DocumentStatusIndex {
    PRELUAT,
    TRIMIS,
    ERONAT,
    // Textul a fost indexat sincron cu succes; imaginile se proceseaza async (embedder_service).
    INDEXED_TEXT_ONLY,
    // Text + imagini indexate cu succes (sau documentul nu are imagini).
    INDEXED,
    // Textul e indexat, dar procesarea imaginilor a esuat definitiv.
    FAILED_IMAGES
}

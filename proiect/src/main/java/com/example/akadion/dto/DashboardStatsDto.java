package com.example.akadion.dto;

public record DashboardStatsDto(
    long cursuriActive,
    long cursuriInactive,
    long utilizatoriActivi,
    long utilizatoriPending
) {}

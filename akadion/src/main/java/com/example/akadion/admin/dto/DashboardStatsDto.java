package com.example.akadion.admin.dto;

public record DashboardStatsDto(
    long cursuriActive,
    long cursuriInactive,
    long utilizatoriActivi,
    long utilizatoriPending
) {}

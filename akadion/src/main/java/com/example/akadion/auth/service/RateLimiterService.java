package com.example.akadion.auth.service;

import com.example.akadion.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// Rate limiter in-memory generic, cu fereastră glisantă, partajat de toate serviciile care au nevoie
// de o limită de cereri/minut. Fiecare apelant își definește propria cheie (ex. "conversatie:" + userId)
// astfel încât limitele diferitelor funcționalități rămân independente între ele.
@Component
public class RateLimiterService {

    private final ConcurrentHashMap<String, Deque<Instant>> cereriPeCheie = new ConcurrentHashMap<>();

    public void verificaLimita(String cheie, int maxCereri, Duration fereastra) {
        Instant acum = Instant.now();
        Instant inceputFereastra = acum.minus(fereastra);

        cereriPeCheie.compute(cheie, (id, timestamps) -> {
            Deque<Instant> deque = timestamps != null ? timestamps : new ConcurrentLinkedDeque<>();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(inceputFereastra)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxCereri) {
                throw new TooManyRequestsException(
                        "Ați depășit limita permisă (" + maxCereri + " cereri / " + fereastra.toMinutes() + " min). Vă rugăm să așteptați puțin.");
            }
            deque.addLast(acum);
            return deque;
        });
    }
}

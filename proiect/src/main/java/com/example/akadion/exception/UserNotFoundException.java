package com.example.akadion.exception;

// Această eroare este aruncată atunci când căutăm un utilizator în baza de date după ID-ul său, dar nu găsim nimic.
// Este mapată la codul HTTP 404 (Not Found), transmițând clientului că resursa cerută nu există.
public class UserNotFoundException extends RuntimeException {

    // Constructorul generează un mesaj simplu și informativ, trecând ID-ul negăsit în mesajul final de eroare.
    public UserNotFoundException(Long id) {
        super("Utilizatorul cu id=" + id + " nu a fost găsit.");
    }
}

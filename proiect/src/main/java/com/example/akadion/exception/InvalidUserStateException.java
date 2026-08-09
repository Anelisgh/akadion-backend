package com.example.akadion.exception;

// Această eroare personalizată este aruncată atunci când administratorul încearcă să facă o acțiune ilogică.
// De exemplu: Să aprobe un utilizator care este deja ACTIV sau să dezactiveze un utilizator care este deja INACTIV.
// Extinde RuntimeException, ceea ce înseamnă că este o eroare ce se produce la rulare și oprește execuția metodei curente.
public class InvalidUserStateException extends RuntimeException {

    // Constructorul primește mesajul explicativ (ex: "Utilizatorul este deja activ") și îl trimite mai departe clasei părinte.
    public InvalidUserStateException(String message) {
        super(message);
    }
}

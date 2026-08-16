package com.example.akadion.curs.service;

import com.example.akadion.curs.entity.Curs;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.exception.ForbiddenOperationException;
import org.springframework.stereotype.Component;

// Centralizează verificarea "profesorul apelant este owner-ul cursului", duplicată anterior
// în CursService, DocumentService și SaptamanaService. Mesajul de eroare rămâne la alegerea
// apelantului, ca să păstrăm exact textele existente per endpoint.
@Component
public class CursOwnershipValidator {

    public void verificaProprietar(Curs curs, Long profesorId, String mesajEroare) {
        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new ForbiddenOperationException(mesajEroare);
        }
    }

    public void verificaProprietarSauAdmin(Curs curs, Long callerId, String callerRole, String mesajEroare) {
        if (!NumeRol.ADMIN.name().equals(callerRole) && !curs.getProfesor().getId().equals(callerId)) {
            throw new ForbiddenOperationException(mesajEroare);
        }
    }
}

package com.example.akadion.curs.service;

import com.example.akadion.curs.entity.Curs;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursOwnershipValidatorTest {

    private final CursOwnershipValidator validator = new CursOwnershipValidator();

    @Test
    void verificaProprietarPassesWhenCallerIsOwner() {
        Curs curs = curs(10L);

        assertThatCode(() -> validator.verificaProprietar(curs, 10L, "mesaj"))
                .doesNotThrowAnyException();
    }

    @Test
    void verificaProprietarThrowsWithGivenMessageWhenCallerIsNotOwner() {
        Curs curs = curs(10L);

        assertThatThrownBy(() -> validator.verificaProprietar(curs, 99L, "Nu aveți acces."))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Nu aveți acces.");
    }

    @Test
    void verificaProprietarSauAdminPassesForOwner() {
        Curs curs = curs(10L);

        assertThatCode(() -> validator.verificaProprietarSauAdmin(curs, 10L, "PROFESOR", "mesaj"))
                .doesNotThrowAnyException();
    }

    @Test
    void verificaProprietarSauAdminPassesForAdminEvenWhenNotOwner() {
        Curs curs = curs(10L);

        assertThatCode(() -> validator.verificaProprietarSauAdmin(curs, 99L, "ADMIN", "mesaj"))
                .doesNotThrowAnyException();
    }

    @Test
    void verificaProprietarSauAdminThrowsForNonOwnerNonAdmin() {
        Curs curs = curs(10L);

        assertThatThrownBy(() -> validator.verificaProprietarSauAdmin(curs, 99L, "PROFESOR", "Nu aveți acces la acest curs."))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Nu aveți acces la acest curs.");
    }

    @Test
    void verificaProprietarSauAdminThrowsWhenCallerIdIsNull() {
        // callerId null trebuie tratat ca "nu e owner", nu ca eroare tehnică (Long.equals e null-safe).
        Curs curs = curs(10L);

        assertThatThrownBy(() -> validator.verificaProprietarSauAdmin(curs, null, "PROFESOR", "mesaj"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private Curs curs(Long profesorId) {
        User profesor = new User();
        profesor.setId(profesorId);

        Curs curs = new Curs();
        curs.setId(1L);
        curs.setProfesor(profesor);
        return curs;
    }
}

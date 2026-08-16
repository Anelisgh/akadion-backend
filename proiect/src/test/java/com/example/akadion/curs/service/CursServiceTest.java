package com.example.akadion.curs.service;

import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.common.entity.User;
import com.example.akadion.curs.dto.CursResponseDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.quiz.repository.IncercareQuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CursServiceTest {

    @Mock
    private CursRepository cursRepository;
    @Mock
    private SaptamanaRepository saptamanaRepository;
    @Mock
    private UserCursRepository userCursRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private IncercareQuizRepository incercareQuizRepository;
    @Mock
    private CursOwnershipValidator cursOwnershipValidator;

    @InjectMocks
    private CursService cursService;

    // Verifică faptul că numărătorile agregate (o singură interogare per listă, nu una per curs)
    // sunt mapate corect la fiecare curs — riscul principal introdus de optimizarea N+1.
    @Test
    void listaToateCursurile_mapeazaCorectNumarStudentiSiSaptamaniPerCurs_dinInterogarileBatch() {
        User profesor = User.builder().id(10L).nume("Popescu").prenume("Ion").build();
        Curs cursA = Curs.builder().id(1L).denumire("Curs A").profesor(profesor).activ(true).build();
        Curs cursB = Curs.builder().id(2L).denumire("Curs B").profesor(profesor).activ(true).build();

        when(cursRepository.findAllWithProfesor()).thenReturn(List.of(cursA, cursB));
        when(saptamanaRepository.countByCursIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, 7L}));
        when(userCursRepository.countActiveByCursIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 5L}, new Object[]{2L, 1L}));

        List<CursResponseDto> rezultat = cursService.listaToateCursurile();

        Map<Long, CursResponseDto> perId = rezultat.stream()
                .collect(java.util.stream.Collectors.toMap(CursResponseDto::id, dto -> dto));

        assertThat(perId.get(1L).nrSaptamaniCurente()).isEqualTo(3);
        assertThat(perId.get(1L).nrStudentiInscrisi()).isEqualTo(5);
        assertThat(perId.get(2L).nrSaptamaniCurente()).isEqualTo(7);
        assertThat(perId.get(2L).nrStudentiInscrisi()).isEqualTo(1);
    }

    @Test
    void listaToateCursurile_cursFaraIntrariInBatch_primesteZero() {
        User profesor = User.builder().id(10L).nume("Popescu").prenume("Ion").build();
        Curs cursFaraSaptamaniSauStudenti = Curs.builder().id(3L).denumire("Curs C").profesor(profesor).activ(true).build();

        when(cursRepository.findAllWithProfesor()).thenReturn(List.of(cursFaraSaptamaniSauStudenti));
        when(saptamanaRepository.countByCursIdIn(List.of(3L))).thenReturn(List.of());
        when(userCursRepository.countActiveByCursIdIn(List.of(3L))).thenReturn(List.of());

        List<CursResponseDto> rezultat = cursService.listaToateCursurile();

        assertThat(rezultat).hasSize(1);
        assertThat(rezultat.get(0).nrSaptamaniCurente()).isZero();
        assertThat(rezultat.get(0).nrStudentiInscrisi()).isZero();
    }

    @Test
    void listaToateCursurile_listaGoala_nuApeleazaInterogarileBatch() {
        when(cursRepository.findAllWithProfesor()).thenReturn(List.of());

        List<CursResponseDto> rezultat = cursService.listaToateCursurile();

        assertThat(rezultat).isEmpty();
    }
}

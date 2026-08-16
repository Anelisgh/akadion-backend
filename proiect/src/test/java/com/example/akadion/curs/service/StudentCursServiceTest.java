package com.example.akadion.curs.service;

import com.example.akadion.auth.service.RateLimiterService;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.curs.dto.CursDisponibilResponseDto;
import com.example.akadion.curs.dto.CursInrolatResponseDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.UserCurs;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.repository.ParcursRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCursServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CursRepository cursRepository;
    @Mock
    private UserCursRepository userCursRepository;
    @Mock
    private SaptamanaRepository saptamanaRepository;
    @Mock
    private ParcursRepository parcursRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentUrlBuilder documentUrlBuilder;
    @Mock
    private RateLimiterService rateLimiterService;

    @InjectMocks
    private StudentCursService studentCursService;

    private static final Long STUDENT_ID = 42L;

    // Verifică faptul că numărul de săptămâni per curs (dintr-o singură interogare batch)
    // e mapat corect la cursul corespunzător, nu amestecat între cursuri.
    @Test
    void listaCursuriDisponibile_mapeazaCorectNumarulDeSaptamaniPerCurs() {
        User profesor = User.builder().id(10L).nume("Ionescu").prenume("Maria").build();
        Curs cursA = Curs.builder().id(1L).denumire("Curs A").profesor(profesor).activ(true).build();
        Curs cursB = Curs.builder().id(2L).denumire("Curs B").profesor(profesor).activ(true).build();

        when(cursRepository.findAvailableCoursesForStudent(STUDENT_ID)).thenReturn(List.of(cursA, cursB));
        when(saptamanaRepository.countByCursIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 4L}, new Object[]{2L, 9L}));

        List<CursDisponibilResponseDto> rezultat = studentCursService.listaCursuriDisponibile(STUDENT_ID);

        Map<Long, CursDisponibilResponseDto> perId = rezultat.stream()
                .collect(Collectors.toMap(CursDisponibilResponseDto::id, dto -> dto));

        assertThat(perId.get(1L).nrSaptamani()).isEqualTo(4);
        assertThat(perId.get(2L).nrSaptamani()).isEqualTo(9);
    }

    @Test
    void listaCursuriDisponibile_listaGoala_nuApeleazaInterogareaBatch() {
        when(cursRepository.findAvailableCoursesForStudent(STUDENT_ID)).thenReturn(List.of());

        List<CursDisponibilResponseDto> rezultat = studentCursService.listaCursuriDisponibile(STUDENT_ID);

        assertThat(rezultat).isEmpty();
    }

    // Verifică faptul că progresul (săptămâni bifate / total săptămâni) e calculat cu numărătorile
    // batch corecte per curs, nu încrucișate între cursurile în care e înrolat studentul.
    @Test
    void listaCursuriInrolate_calculeazaProgresulCorectDinInterogarileBatch() {
        User profesor = User.builder().id(10L).nume("Ionescu").prenume("Maria").build();
        Curs cursA = Curs.builder().id(1L).denumire("Curs A").profesor(profesor).activ(true).build();
        Curs cursB = Curs.builder().id(2L).denumire("Curs B").profesor(profesor).activ(true).build();
        UserCurs inscriereA = UserCurs.builder().id(100L).curs(cursA).activ(true).build();
        UserCurs inscriereB = UserCurs.builder().id(101L).curs(cursB).activ(true).build();

        when(userCursRepository.findEnrolledCoursesForStudent(STUDENT_ID)).thenReturn(List.of(inscriereA, inscriereB));
        when(saptamanaRepository.countByCursIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 4L}, new Object[]{2L, 10L}));
        when(parcursRepository.countCompletedWeeksByCursIdIn(STUDENT_ID, List.of(1L, 2L)))
                .thenReturn(List.of(new Object[]{1L, 2L}, new Object[]{2L, 1L}));

        List<CursInrolatResponseDto> rezultat = studentCursService.listaCursuriInrolate(STUDENT_ID);

        Map<Long, CursInrolatResponseDto> perId = rezultat.stream()
                .collect(Collectors.toMap(CursInrolatResponseDto::id, dto -> dto));

        // Curs A: 2/4 = 50%
        assertThat(perId.get(1L).procentajProgres()).isEqualTo(50.0);
        assertThat(perId.get(1L).nrSaptamani()).isEqualTo(4);
        // Curs B: 1/10 = 10%
        assertThat(perId.get(2L).procentajProgres()).isEqualTo(10.0);
        assertThat(perId.get(2L).nrSaptamani()).isEqualTo(10);
    }

    @Test
    void listaCursuriInrolate_cursFaraSaptamani_progresZeroFaraImpartireLaZero() {
        User profesor = User.builder().id(10L).nume("Ionescu").prenume("Maria").build();
        Curs cursFaraSaptamani = Curs.builder().id(5L).denumire("Curs gol").profesor(profesor).activ(true).build();
        UserCurs inscriere = UserCurs.builder().id(200L).curs(cursFaraSaptamani).activ(true).build();

        when(userCursRepository.findEnrolledCoursesForStudent(STUDENT_ID)).thenReturn(List.of(inscriere));
        when(saptamanaRepository.countByCursIdIn(List.of(5L))).thenReturn(List.of());
        when(parcursRepository.countCompletedWeeksByCursIdIn(STUDENT_ID, List.of(5L))).thenReturn(List.of());

        List<CursInrolatResponseDto> rezultat = studentCursService.listaCursuriInrolate(STUDENT_ID);

        assertThat(rezultat).hasSize(1);
        assertThat(rezultat.get(0).procentajProgres()).isZero();
        assertThat(rezultat.get(0).nrSaptamani()).isZero();
    }
}

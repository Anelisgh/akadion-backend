package com.example.akadion.akychat.service;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.entity.Conversatie;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.akychat.entity.MesajChat;
import com.example.akadion.common.entity.Rol;
import com.example.akadion.akychat.entity.RolMesaj;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.TooManyRequestsException;
import com.example.akadion.akychat.repository.ConversatieRepository;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.akychat.repository.MesajChatRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversatieServiceTest {

    @Mock
    private ConversatieRepository conversatieRepository;
    @Mock
    private MesajChatRepository mesajChatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CursRepository cursRepository;
    @Mock
    private UserCursRepository userCursRepository;
    @Mock
    private RagChatService ragChatService;

    private ConversatieService conversatieService;

    @BeforeEach
    void setUp() {
        conversatieService = new ConversatieService(
                conversatieRepository,
                mesajChatRepository,
                userRepository,
                cursRepository,
                userCursRepository,
                ragChatService,
                new RateLimiterService()
        );
    }

    @Test
    void salveazaIntrebare_creeazaConversatieNoua() {
        User student = user(1L, "STUDENT");
        Curs curs = curs(10L, 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(cursRepository.findById(10L)).thenReturn(Optional.of(curs));
        when(userCursRepository.existsByStudentIdAndCursIdAndActivTrue(1L, 10L)).thenReturn(true);
        when(conversatieRepository.save(any(Conversatie.class))).thenAnswer(inv -> {
            Conversatie c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(mesajChatRepository.save(any(MesajChat.class))).thenAnswer(inv -> {
            MesajChat m = inv.getArgument(0);
            m.setId(1000L);
            return m;
        });

        MesajChat rezultat = conversatieService.salveazaIntrebare(null, 1L, 10L, "Ce este ORM?");

        assertThat(rezultat.getRol()).isEqualTo(RolMesaj.UTILIZATOR);
        assertThat(rezultat.getContinut()).isEqualTo("Ce este ORM?");
        assertThat(rezultat.getConversatie().getTitlu()).isEqualTo("Ce este ORM?");

        verify(conversatieRepository, times(1)).save(any(Conversatie.class));
        verify(mesajChatRepository, times(1)).save(any(MesajChat.class));
    }

    @Test
    void salveazaIntrebare_aruncaEroareLaPeste10MesajePeMinut() {
        User student = user(1L, "STUDENT");
        Curs curs = curs(10L, 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(cursRepository.findById(10L)).thenReturn(Optional.of(curs));
        when(userCursRepository.existsByStudentIdAndCursIdAndActivTrue(1L, 10L)).thenReturn(true);
        when(conversatieRepository.save(any(Conversatie.class))).thenAnswer(inv -> {
            Conversatie c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(mesajChatRepository.save(any(MesajChat.class))).thenReturn(new MesajChat());

        // Trimitem 10 mesaje (limita este 10)
        for (int i = 0; i < 10; i++) {
            conversatieService.salveazaIntrebare(null, 1L, 10L, "Mesaj " + i);
        }

        // Al 11-lea ar trebui sa pice
        assertThatThrownBy(() -> conversatieService.salveazaIntrebare(null, 1L, 10L, "Mesaj 11"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void obtineRaspunsRag_faraIntrebareaCurentaInIstoric() {
        Conversatie conv = new Conversatie();
        conv.setId(100L);
        conv.setCurs(curs(10L, 2L));

        // RAG istoric needs to exclude current question. If last message matches current question, remove it.
        MesajChat vechi = new MesajChat();
        vechi.setRol(RolMesaj.UTILIZATOR);
        vechi.setContinut("Intrebare veche");
        vechi.setCreatedAt(OffsetDateTime.now().minusMinutes(5));

        MesajChat curent = new MesajChat();
        curent.setRol(RolMesaj.UTILIZATOR);
        curent.setContinut("Intrebare curenta");
        curent.setCreatedAt(OffsetDateTime.now());

        // Lista returnata descrescator (cel mai nou primul)
        when(conversatieRepository.findById(100L)).thenReturn(Optional.of(conv));
        when(mesajChatRepository.findTop10ByConversatieIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(curent, vechi));
        when(ragChatService.intreabaAky(anyLong(), anyLong(), any(AkyChatRequestDto.class)))
                .thenReturn(new AkyChatResponseDto("Raspuns RAG", null));

        AkyChatResponseDto raspuns = conversatieService.obtineRaspunsRag(100L, 1L, "Intrebare curenta");

        ArgumentCaptor<AkyChatRequestDto> captor = ArgumentCaptor.forClass(AkyChatRequestDto.class);
        verify(ragChatService).intreabaAky(eq(1L), eq(10L), captor.capture());

        AkyChatRequestDto trimis = captor.getValue();
        assertThat(trimis.intrebare()).isEqualTo("Intrebare curenta");
        assertThat(trimis.istoricConversatie()).hasSize(1);
        assertThat(trimis.istoricConversatie().get(0).text()).isEqualTo("Intrebare veche");
        assertThat(raspuns.raspuns()).isEqualTo("Raspuns RAG");
    }

    private User user(Long id, String rolDenumire) {
        User user = new User();
        user.setId(id);
        Rol rol = new Rol();
        rol.setDenumire(rolDenumire);
        user.setRol(rol);
        return user;
    }

    private Curs curs(Long id, Long profId) {
        Curs curs = new Curs();
        curs.setId(id);
        User prof = new User();
        prof.setId(profId);
        curs.setProfesor(prof);
        return curs;
    }
}

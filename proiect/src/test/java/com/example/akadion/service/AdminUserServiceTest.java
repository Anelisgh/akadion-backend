package com.example.akadion.service;

import com.example.akadion.dto.ActionResponseDto;
import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.InvalidUserStateException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserCursRepository;
import com.example.akadion.repository.UserRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StareContRepository stareContRepository;

    @Mock
    private UserCursRepository userCursRepository;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private CursService cursService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, stareContRepository, userCursRepository, keycloakAdminService, cursService);
    }

    @Test
    void pendingUserAppearsInAdminList() {
        User pendingUser = user(11L, "sub-pending", "pending@akadion.test", "PENDING", "STUDENT");
        pendingUser.setCreatedAt(OffsetDateTime.parse("2026-07-15T10:15:30+03:00"));
        when(userRepository.findByStareCont_Denumire("PENDING")).thenReturn(List.of(pendingUser));

        List<UserPendingDto> result = adminUserService.listaUtilizatori("PENDING");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().mail()).isEqualTo("pending@akadion.test");
        assertThat(result.getFirst().rolDorit()).isEqualTo("STUDENT");
        assertThat(result.getFirst().stare()).isEqualTo("PENDING");
        assertThat(result.getFirst().createdAt()).isEqualTo(pendingUser.getCreatedAt());
    }

    @Test
    void countUtilizatoriExcludesAdmin() {
        when(userRepository.countNonAdminByStareCont_Denumire("ACTIV")).thenReturn(2L);

        long count = adminUserService.countUtilizatori("ACTIV");

        assertThat(count).isEqualTo(2L);
        verify(userRepository).countNonAdminByStareCont_Denumire("ACTIV");
    }

    @Test
    void adminApprovesPendingUser() {
        User pendingUser = user(12L, "sub-user", "user@akadion.test", "PENDING", "STUDENT");
        when(userRepository.findById(12L)).thenReturn(Optional.of(pendingUser));
        when(stareContRepository.findByDenumire("ACTIV")).thenReturn(Optional.of(stare("ACTIV")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionResponseDto response = adminUserService.approveUser(12L);

        assertThat(response.message()).contains("aprobat");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStareCont().getDenumire()).isEqualTo("ACTIV");
    }

    @Test
    void adminRejectsPendingUser() {
        User pendingUser = user(13L, "sub-user", "user@akadion.test", "PENDING", "PROFESOR");
        when(userRepository.findById(13L)).thenReturn(Optional.of(pendingUser));
        when(stareContRepository.findByDenumire("RESPINS")).thenReturn(Optional.of(stare("RESPINS")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionResponseDto response = adminUserService.rejectUser(13L);

        assertThat(response.message()).contains("respins");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStareCont().getDenumire()).isEqualTo("RESPINS");
        assertThat(captor.getValue().getNrRespingeri()).isEqualTo(1);
    }

    @Test
    void cannotApproveAlreadyActiveUser() {
        when(userRepository.findById(14L)).thenReturn(Optional.of(user(14L, "sub-active", "active@akadion.test", "ACTIV", "STUDENT")));

        assertThatThrownBy(() -> adminUserService.approveUser(14L))
                .isInstanceOf(InvalidUserStateException.class)
                .hasMessageContaining("ACTIV");
    }

    @Test
    void cannotRejectAdminUser() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(user(15L, "sub-admin", "admin@akadion.test", "PENDING", "ADMIN")));

        assertThatThrownBy(() -> adminUserService.rejectUser(15L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void throwsNotFoundForMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.approveUser(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("id=99");
    }

    private User user(Long id, String sub, String mail, String stareName, String rolName) {
        User user = new User();
        user.setId(id);
        user.setIdKeycloak(sub);
        user.setMail(mail);
        user.setNume("Test");
        user.setPrenume("User");
        user.setStareCont(stare(stareName));
        user.setRol(rol(rolName));
        user.setNrRespingeri(0);
        return user;
    }

    private Rol rol(String denumire) {
        Rol rol = new Rol();
        rol.setDenumire(denumire);
        return rol;
    }

    private StareCont stare(String denumire) {
        StareCont stareCont = new StareCont();
        stareCont.setDenumire(denumire);
        return stareCont;
    }
}

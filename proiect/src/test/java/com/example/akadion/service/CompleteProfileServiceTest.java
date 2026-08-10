package com.example.akadion.service;

import com.example.akadion.dto.CompleteProfileRequestDto;
import com.example.akadion.dto.CompleteProfileResponseDto;
import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.InvalidUserStateException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private StareContRepository stareContRepository;

    @Mock
    private AuditLogService auditLogService;

    private CompleteProfileService completeProfileService;

    @BeforeEach
    void setUp() {
        completeProfileService = new CompleteProfileService(userRepository, rolRepository, stareContRepository, auditLogService);
    }

    @Test
    void updatesExistingSkeletonUserToPendingStudent() {
        User skeletonUser = existingUser("sub-student", "student@akadion.test", "INCOMPLET", null);
        when(userRepository.findByIdKeycloak("sub-student")).thenReturn(Optional.of(skeletonUser));
        when(userRepository.findByMail("student@akadion.test")).thenReturn(Optional.of(skeletonUser));
        when(rolRepository.findByDenumire("STUDENT")).thenReturn(Optional.of(rol("STUDENT")));
        when(stareContRepository.findByDenumire("PENDING")).thenReturn(Optional.of(stare("PENDING")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteProfileResponseDto response = completeProfileService.completeaza("sub-student", "student@akadion.test", request("STUDENT"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved).isSameAs(skeletonUser);
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getIdKeycloak()).isEqualTo("sub-student");
        assertThat(saved.getMail()).isEqualTo("student@akadion.test");
        assertThat(saved.getNume()).isEqualTo("Popescu");
        assertThat(saved.getPrenume()).isEqualTo("Andrei");
        assertThat(saved.getFacultate()).isEqualTo("FMI");
        assertThat(saved.getRol().getDenumire()).isEqualTo("STUDENT");
        assertThat(saved.getStareCont().getDenumire()).isEqualTo("PENDING");
        assertThat(response.mail()).isEqualTo("student@akadion.test");
        assertThat(response.rolDorit()).isEqualTo("STUDENT");
        assertThat(response.stare()).isEqualTo("PENDING");
    }

    @Test
    void updatesExistingSkeletonUserToPendingProfesor() {
        User skeletonUser = existingUser("sub-profesor", "profesor@akadion.test", "INCOMPLET", null);
        when(userRepository.findByIdKeycloak("sub-profesor")).thenReturn(Optional.of(skeletonUser));
        when(userRepository.findByMail("profesor@akadion.test")).thenReturn(Optional.of(skeletonUser));
        when(rolRepository.findByDenumire("PROFESOR")).thenReturn(Optional.of(rol("PROFESOR")));
        when(stareContRepository.findByDenumire("PENDING")).thenReturn(Optional.of(stare("PENDING")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        completeProfileService.completeaza("sub-profesor", "profesor@akadion.test", request("PROFESOR"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRol().getDenumire()).isEqualTo("PROFESOR");
        assertThat(captor.getValue().getStareCont().getDenumire()).isEqualTo("PENDING");
    }

    @Test
    void rejectsAdminRoleFromProfileForm() {
        User skeletonUser = existingUser("sub-admin-attempt", "admin-attempt@akadion.test", "INCOMPLET", null);
        when(userRepository.findByIdKeycloak("sub-admin-attempt")).thenReturn(Optional.of(skeletonUser));
        when(userRepository.findByMail("admin-attempt@akadion.test")).thenReturn(Optional.of(skeletonUser));
        when(rolRepository.findByDenumire("ADMIN")).thenReturn(Optional.of(rol("ADMIN")));
        when(stareContRepository.findByDenumire("PENDING")).thenReturn(Optional.of(stare("PENDING")));

        assertThatThrownBy(() -> completeProfileService.completeaza(
                "sub-admin-attempt",
                "admin-attempt@akadion.test",
                request("ADMIN")
        )).isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void throwsUserNotFoundWhenSkeletonUserDoesNotExist() {
        when(userRepository.findByIdKeycloak("sub-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeProfileService.completeaza(
                "sub-missing",
                "missing@akadion.test",
                request("STUDENT")
        )).isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("nu are cont local");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsDuplicateEmailForDifferentKeycloakSubject() {
        User skeletonUser = existingUser("sub-new", "duplicate@akadion.test", "INCOMPLET", null);
        when(userRepository.findByIdKeycloak("sub-new")).thenReturn(Optional.of(skeletonUser));
        when(userRepository.findByMail("duplicate@akadion.test"))
                .thenReturn(Optional.of(existingUser("sub-existing", "duplicate@akadion.test", "ACTIV", "STUDENT")));

        assertThatThrownBy(() -> completeProfileService.completeaza(
                "sub-new",
                "duplicate@akadion.test",
                request("STUDENT")
        )).isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("emailul");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "ACTIV", "INACTIV"})
    void rejectsProfileCompletionForUsersInDisallowedStates(String stareCurenta) {
        when(userRepository.findByIdKeycloak("sub-user"))
                .thenReturn(Optional.of(existingUser("sub-user", "user@akadion.test", stareCurenta, "STUDENT")));

        assertThatThrownBy(() -> completeProfileService.completeaza(
                "sub-user",
                "user@akadion.test",
                request("STUDENT")
        )).isInstanceOf(InvalidUserStateException.class)
                .hasMessageContaining(stareCurenta);
    }

    @Test
    void preservesAuthenticatedIdentityAndTrustedEmailOnly() {
        User skeletonUser = existingUser("sub-trusted", "old@akadion.test", "INCOMPLET", null);
        when(userRepository.findByIdKeycloak("sub-trusted")).thenReturn(Optional.of(skeletonUser));
        when(userRepository.findByMail("trusted@akadion.test")).thenReturn(Optional.of(skeletonUser));
        when(rolRepository.findByDenumire("STUDENT")).thenReturn(Optional.of(rol("STUDENT")));
        when(stareContRepository.findByDenumire("PENDING")).thenReturn(Optional.of(stare("PENDING")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        completeProfileService.completeaza("sub-trusted", "trusted@akadion.test", request("STUDENT"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getIdKeycloak()).isEqualTo("sub-trusted");
        assertThat(captor.getValue().getMail()).isEqualTo("trusted@akadion.test");
    }

    private CompleteProfileRequestDto request(String rolDorit) {
        return new CompleteProfileRequestDto("Popescu", "Andrei", "FMI", rolDorit);
    }

    private Rol rol(String denumire) {
        Rol rol = new Rol();
        rol.setDenumire(denumire);
        return rol;
    }

    private StareCont stare(String denumire) {
        StareCont stare = new StareCont();
        stare.setDenumire(denumire);
        return stare;
    }

    private User existingUser(String sub, String mail, String stareName, String rolName) {
        User user = new User();
        user.setId(7L);
        user.setIdKeycloak(sub);
        user.setMail(mail);
        user.setStareCont(stare(stareName));
        user.setNrRespingeri(0);

        if (rolName != null) {
            user.setRol(rol(rolName));
        }

        return user;
    }
}

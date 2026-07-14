package com.example.akadion.service;

import com.example.akadion.dto.CompleteProfileRequestDto;
import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileService {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;

    @Transactional
    public void completeaza(String subKeycloak, CompleteProfileRequestDto dto) {
        User user = userRepository.findByIdKeycloak(subKeycloak)
                .orElseThrow(() -> new UserNotFoundException(0L));

        StareCont pending = stareContRepository.findByDenumire("PENDING")
                .orElseThrow(() -> new IllegalStateException("Starea PENDING lipsește din DB — verifică DataSeeder"));

        String stareCurenta = user.getStareCont().getDenumire();
        if (!"INCOMPLET".equals(stareCurenta) && !"RESPINS".equals(stareCurenta)) {
            throw new com.example.akadion.exception.InvalidUserStateException(
                    "Completarea profilului este permisă doar pentru conturile INCOMPLET sau RESPINS (stare curentă: " + stareCurenta + ").");
        }

        Rol rol = rolRepository.findByDenumire(dto.rolDorit())
                .orElseThrow(() -> new IllegalStateException("Rolul '" + dto.rolDorit() + "' lipsește din DB — verifică DataSeeder"));

        log.info("Completare profil pentru sub={}: nume={}, prenume={}, rolDorit={}",
                subKeycloak, dto.nume(), dto.prenume(), dto.rolDorit());

        user.setNume(dto.nume());
        user.setPrenume(dto.prenume());
        user.setFacultate(dto.facultate());
        user.setRol(rol);
        user.setStareCont(pending);

        userRepository.save(user);
        log.info("Profil salvat pentru sub={} în starea PENDING.", subKeycloak);
    }
}

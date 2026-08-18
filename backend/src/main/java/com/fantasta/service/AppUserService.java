package com.fantasta.service;

import com.fantasta.dto.AuthUserDto;
import com.fantasta.dto.AdminUserDto;
import com.fantasta.dto.CreateUserRequest;
import com.fantasta.dto.LoginRequest;
import com.fantasta.model.AppUserEntity;
import com.fantasta.model.ParticipantEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class AppUserService {

    @Inject
    PasswordService passwordService;

    @Transactional
    public AppUserEntity createUser(CreateUserRequest request) {
        validateCreate(request);

        String username = normalizeUsername(request.username);
        AppUserEntity existing = AppUserEntity.find("username", username).firstResult();
        if (existing != null) {
            throw new BadRequestException("Username gia' esistente");
        }

        ParticipantEntity participant = null;
        if (request.participantId != null) {
            participant = ParticipantEntity.findById(request.participantId);
            if (participant == null) {
                throw new BadRequestException("Nome squadra non valido");
            }
        } else if (!"admin".equals(normalizeRole(request.role)) && !isBlank(request.participantName)) {
            String participantName = request.participantName.trim();
            if (ParticipantEntity.find("lower(name) = ?1", participantName.toLowerCase(Locale.ROOT)).firstResult() != null) {
                throw new BadRequestException("Nome squadra gia' esistente");
            }
            participant = new ParticipantEntity();
            participant.name = participantName;
            participant.totalCredits = request.totalCredits == null ? 500 : request.totalCredits;
            if (participant.totalCredits <= 0) throw new BadRequestException("Crediti non validi");
            participant.persist();
        }

        if (participant != null && AppUserEntity.find("participant", participant).firstResult() != null) {
            throw new BadRequestException("La squadra ha gia' un account associato");
        }

        AppUserEntity user = new AppUserEntity();
        user.username = username;
        user.passwordHash = passwordService.hash(request.password);
        user.role = normalizeRole(request.role);
        user.participant = participant;
        user.enabled = true;
        user.mustChangePassword = !"admin".equals(user.role) && !Boolean.TRUE.equals(request.permanentPassword);
        user.persist();
        return user;
    }

    @Transactional
    public AppUserEntity authenticate(LoginRequest request) {
        if (request == null || isBlank(request.username) || isBlank(request.password)) {
            throw new NotAuthorizedException("Credenziali non valide");
        }

        AppUserEntity user = AppUserEntity.find(
                "select u from AppUserEntity u left join fetch u.participant where u.username = ?1",
                normalizeUsername(request.username)).firstResult();
        if (user == null || !user.enabled || !passwordService.verify(request.password, user.passwordHash)) {
            throw new NotAuthorizedException("Credenziali non valide");
        }
        return user;
    }

    @Transactional
    public List<AdminUserDto> listUsers() {
        return AppUserEntity.<AppUserEntity>list("order by username").stream().map(user -> {
            Long participantId = user.participant == null ? null : user.participant.id;
            ParticipantEntity participant = participantId == null ? null : ParticipantEntity.findById(participantId);
            return new AdminUserDto(user.username, user.role, participantId,
                    participant == null ? null : participant.name, user.mustChangePassword);
        }).toList();
    }

    @Transactional
    public AuthUserDto toDto(AppUserEntity user) {
        Long participantId = user.participant == null ? null : user.participant.id;
        ParticipantEntity participant = participantId == null ? null : ParticipantEntity.findById(participantId);
        String participantName = participant == null ? null : participant.name;
        String effectiveRole = user.mustChangePassword ? "password-change" : user.role;
        return new AuthUserDto(user.username, List.of(effectiveRole), participantId,
                participantName, user.mustChangePassword);
    }

    @Transactional
    public AppUserEntity changePassword(String username, String password) {
        if (isBlank(password) || password.length() < 4) {
            throw new BadRequestException("La password deve contenere almeno 4 caratteri");
        }
        AppUserEntity user = AppUserEntity.find("username", normalizeUsername(username)).firstResult();
        if (user == null || !user.enabled) throw new NotAuthorizedException("Utente non valido");
        if (passwordService.verify(password, user.passwordHash)) {
            throw new BadRequestException("La nuova password deve essere diversa da quella temporanea");
        }
        user.passwordHash = passwordService.hash(password);
        user.mustChangePassword = false;
        return user;
    }

    private void validateCreate(CreateUserRequest request) {
        if (request == null) {
            throw new BadRequestException("Dati utente mancanti");
        }
        if (isBlank(request.username)) {
            throw new BadRequestException("Username obbligatorio");
        }
        if (isBlank(request.password)) {
            throw new BadRequestException("Password obbligatoria");
        }
        if (request.password.length() < 4) {
            throw new BadRequestException("La password deve contenere almeno 4 caratteri");
        }
        normalizeRole(request.role);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (!"admin".equals(normalized) && !"user".equals(normalized)) {
            throw new BadRequestException("Ruolo non valido");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

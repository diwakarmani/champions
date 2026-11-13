package com.propertyapp.service.agent;

import com.propertyapp.dto.agent.AgentApplicationDTO;
import com.propertyapp.dto.agent.AgentApplicationRequest;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.entity.agent.AgentApplication;
import com.propertyapp.entity.agent.AgentApplicationStatus;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.DuplicateResourceException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.agent.AgentApplicationRepository;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.Constants;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentApplicationServiceImpl implements AgentApplicationService {

    private static final String ENTITY_TYPE = "AGENT_APPLICATION";

    private final AgentApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AgentNotifier notifier;
    private final com.propertyapp.storage.ImageRefs imageRefs;

    @Override
    @Transactional
    public AgentApplicationDTO apply(AgentApplicationRequest request) {
        Long userId = currentUserId();
        User applicant = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (applicant.isAgentSuspended()) {
            throw new BadRequestException("Your agent account is suspended. Please contact support.");
        }
        if (hasRole(applicant, Constants.ROLE_REALTOR)) {
            throw new BadRequestException("You are already an agent");
        }
        if (applicationRepository.existsByUserIdAndStatus(userId, AgentApplicationStatus.PENDING)) {
            throw new DuplicateResourceException("You already have an application waiting for review");
        }

        AgentApplication application = AgentApplication.builder()
                .user(applicant)
                .licenseNumber(request.getLicenseNumber().trim())
                .licenseState(request.getLicenseState().toUpperCase(Locale.ROOT))
                .brokerageName(request.getBrokerageName().trim())
                .yearsExperience(request.getYearsExperience())
                .serviceAreas(StringUtils.hasText(request.getServiceAreas()) ? request.getServiceAreas().trim() : null)
                .bio(StringUtils.hasText(request.getBio()) ? request.getBio().trim() : null)
                .photoUrl(StringUtils.hasText(request.getPhotoUrl()) ? imageRefs.toStored(request.getPhotoUrl()) : null)
                .build();
        try {
            application = applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException raced) {
            // Two requests at once: the partial unique index lets only one through.
            throw new DuplicateResourceException("You already have an application waiting for review");
        }

        notifier.notifyInApp(adminIds(), NotificationType.AGENT_APPLICATION_SUBMITTED,
                "New agent application",
                applicant.getFirstName() + " " + applicant.getLastName() + " has applied to become an agent.",
                ENTITY_TYPE, application.getId());

        log.info("Agent application {} submitted by user {}", application.getId(), userId);
        return AgentApplicationDTO.from(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentApplicationDTO> getMyApplications() {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId()).stream()
                .map(AgentApplicationDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<AgentApplicationDTO> getApplications(AgentApplicationStatus status, Pageable pageable) {
        var page = status == null
                ? applicationRepository.findAll(pageable)
                : applicationRepository.findByStatus(status, pageable);
        return PageResponse.of(page.map(AgentApplicationDTO::from));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "users", allEntries = true) // roles or agent status changed
    public AgentApplicationDTO approve(Long applicationId) {
        Long adminId = currentUserId();
        AgentApplication application = findReviewable(applicationId, adminId);
        User applicant = application.getUser();

        if (applicant.isDeleted() || !applicant.isActive()) {
            throw new BadRequestException("This applicant's account is no longer active");
        }
        if (applicant.isAgentSuspended()) {
            throw new BadRequestException("This person is a suspended agent: reinstate them instead");
        }

        Role realtor = roleRepository.findByName(Constants.ROLE_REALTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", Constants.ROLE_REALTOR));
        applicant.addRole(realtor);
        applicant.setBrokerageName(application.getBrokerageName());
        applicant.setLicenseNumber(application.getLicenseNumber());
        applicant.setLicenseState(application.getLicenseState());
        applicant.setYearsExperience(application.getYearsExperience());
        applicant.setServiceAreas(application.getServiceAreas());
        if (application.getBio() != null) {
            applicant.setBio(application.getBio());
        }
        if (application.getPhotoUrl() != null) {
            applicant.setProfileImageUrl(application.getPhotoUrl());
        }
        application.approve(adminId);
        userRepository.save(applicant);
        application = applicationRepository.saveAndFlush(application);

        notifier.tell(applicant, NotificationType.AGENT_APPLICATION_APPROVED,
                "You are now an agent",
                "Your application was approved. You can start creating listings now.",
                ENTITY_TYPE, application.getId());

        log.info("Agent application {} approved by admin {}; user {} is now an agent",
                applicationId, adminId, applicant.getId());
        return AgentApplicationDTO.from(application);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AgentApplicationDTO reject(Long applicationId, String reason) {
        Long adminId = currentUserId();
        AgentApplication application = findReviewable(applicationId, adminId);
        String trimmedReason = reason.trim();

        application.reject(adminId, trimmedReason);
        application = applicationRepository.saveAndFlush(application);

        notifier.tell(application.getUser(), NotificationType.AGENT_APPLICATION_REJECTED,
                "Your agent application was not approved",
                "Reason: " + trimmedReason + "\nYou can update your details and apply again.",
                ENTITY_TYPE, application.getId());

        log.info("Agent application {} rejected by admin {}", applicationId, adminId);
        return AgentApplicationDTO.from(application);
    }

    private AgentApplication findReviewable(Long applicationId, Long adminId) {
        AgentApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent application", "id", applicationId));
        if (application.getUser().getId().equals(adminId)) {
            throw new BadRequestException("You cannot review your own application");
        }
        if (application.getStatus() != AgentApplicationStatus.PENDING) {
            throw new BadRequestException("This application has already been reviewed");
        }
        return application;
    }

    private List<Long> adminIds() {
        return userRepository.findByRole(Constants.ROLE_SUPER_ADMIN, PageRequest.of(0, 100))
                .map(User::getId)
                .getContent();
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(role -> roleName.equals(role.getName()));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
    }
}

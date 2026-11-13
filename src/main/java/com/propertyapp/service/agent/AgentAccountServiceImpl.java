package com.propertyapp.service.agent;

import com.propertyapp.dto.agent.AgentStatus;
import com.propertyapp.dto.agent.AgentSummaryDTO;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.entity.agent.AgentStatusEvent;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.agent.AgentStatusEventRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.Constants;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspension changes no listing: a suspended agent's listings leave public results because the
 * active-agent rule excludes them, and they return on reinstate (ADR-0004, ADR-0005).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentAccountServiceImpl implements AgentAccountService {

    private static final String ENTITY_TYPE = "USER";

    private final UserRepository userRepository;
    private final AgentStatusEventRepository statusEventRepository;
    private final AgentNotifier notifier;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<AgentSummaryDTO> getAgents(AgentStatus status, Pageable pageable) {
        Page<User> agents;
        if (status == null) {
            agents = userRepository.findByRole(Constants.ROLE_REALTOR, pageable);
        } else if (status == AgentStatus.SUSPENDED) {
            agents = userRepository.findSuspendedByRole(Constants.ROLE_REALTOR, pageable);
        } else {
            agents = userRepository.findNotSuspendedByRole(Constants.ROLE_REALTOR, pageable);
        }
        return PageResponse.of(agents.map(AgentSummaryDTO::from));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "users", allEntries = true) // roles or agent status changed
    public AgentSummaryDTO suspend(Long agentId, String reason) {
        Long adminId = currentUserId();
        User agent = findAgent(agentId, adminId);
        if (agent.isAgentSuspended()) {
            throw new BadRequestException("This agent is already suspended");
        }
        String trimmedReason = reason.trim();

        agent.suspendAsAgent(trimmedReason);
        agent = userRepository.saveAndFlush(agent);
        record(agentId, AgentStatusEvent.SUSPENDED, trimmedReason, adminId);

        notifier.tell(agent, NotificationType.AGENT_SUSPENDED,
                "Your agent account has been suspended",
                "Reason: " + trimmedReason + "\nYour listings are hidden and cannot be changed until this is resolved.",
                ENTITY_TYPE, agentId);

        log.info("Agent {} suspended by admin {}", agentId, adminId);
        return AgentSummaryDTO.from(agent);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "users", allEntries = true) // roles or agent status changed
    public AgentSummaryDTO reinstate(Long agentId) {
        Long adminId = currentUserId();
        User agent = findAgent(agentId, adminId);
        if (!agent.isAgentSuspended()) {
            throw new BadRequestException("This agent is not suspended");
        }

        agent.reinstateAsAgent();
        agent = userRepository.saveAndFlush(agent);
        record(agentId, AgentStatusEvent.REINSTATED, null, adminId);

        notifier.tell(agent, NotificationType.AGENT_REINSTATED,
                "Your agent account has been reinstated",
                "Your listings are visible again and you can manage them as before.",
                ENTITY_TYPE, agentId);

        log.info("Agent {} reinstated by admin {}", agentId, adminId);
        return AgentSummaryDTO.from(agent);
    }

    private User findAgent(Long agentId, Long adminId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .filter(found -> found.getRoles().stream()
                        .anyMatch(role -> Constants.ROLE_REALTOR.equals(role.getName())))
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        if (user.getId().equals(adminId)) {
            throw new BadRequestException("You cannot change your own agent status");
        }
        return user;
    }

    private void record(Long agentId, String action, String reason, Long adminId) {
        statusEventRepository.save(AgentStatusEvent.builder()
                .agentId(agentId)
                .action(action)
                .reason(reason)
                .performedBy(adminId)
                .build());
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
    }
}

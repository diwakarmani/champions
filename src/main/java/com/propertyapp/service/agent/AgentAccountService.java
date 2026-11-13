package com.propertyapp.service.agent;

import com.propertyapp.dto.agent.AgentStatus;
import com.propertyapp.dto.agent.AgentSummaryDTO;
import com.propertyapp.dto.common.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AgentAccountService {

    PageResponse<AgentSummaryDTO> getAgents(AgentStatus status, Pageable pageable);

    AgentSummaryDTO suspend(Long agentId, String reason);

    AgentSummaryDTO reinstate(Long agentId);
}

package com.propertyapp.service.agent;

import com.propertyapp.dto.agent.AgentApplicationDTO;
import com.propertyapp.dto.agent.AgentApplicationRequest;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.entity.agent.AgentApplicationStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AgentApplicationService {

    AgentApplicationDTO apply(AgentApplicationRequest request);

    List<AgentApplicationDTO> getMyApplications();

    PageResponse<AgentApplicationDTO> getApplications(AgentApplicationStatus status, Pageable pageable);

    AgentApplicationDTO approve(Long applicationId);

    AgentApplicationDTO reject(Long applicationId, String reason);
}

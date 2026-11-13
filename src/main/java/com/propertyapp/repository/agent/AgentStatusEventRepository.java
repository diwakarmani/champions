package com.propertyapp.repository.agent;

import com.propertyapp.entity.agent.AgentStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentStatusEventRepository extends JpaRepository<AgentStatusEvent, Long> {

    List<AgentStatusEvent> findByAgentIdOrderByCreatedAtDescIdDesc(Long agentId);
}

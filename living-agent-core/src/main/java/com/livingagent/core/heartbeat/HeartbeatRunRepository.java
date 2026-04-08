package com.livingagent.core.heartbeat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HeartbeatRunRepository extends JpaRepository<HeartbeatRun, String> {
    
    List<HeartbeatRun> findByEmployeeId(String employeeId);
    
    List<HeartbeatRun> findByStatus(String status);
    
    List<HeartbeatRun> findByEmployeeIdAndStatus(String employeeId, String status);
    
    List<HeartbeatRun> findByWakeSource(String wakeSource);
}

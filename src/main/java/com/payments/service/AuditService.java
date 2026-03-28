package com.payments.service;

import com.payments.model.entity.AuditLog;
import com.payments.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, UUID entityId, UUID actorId, String changes) {
        try {
            String ipAddress = null;
            String userAgent = null;

            var requestAttrs = RequestContextHolder.getRequestAttributes();
            if (requestAttrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                ipAddress = extractClientIp(request);
                userAgent = request.getHeader("User-Agent");
            }

            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .actorId(actorId)
                    .changes(changes)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent != null && userAgent.length() > 500
                            ? userAgent.substring(0, 500) : userAgent)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entityId={}", action, entityId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsForEntity(UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtDesc(entityId, pageable);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

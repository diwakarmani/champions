package com.propertyapp.job;

import com.propertyapp.service.auth.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupJob {
    
    private final OtpService otpService;
    
    /**
     * Cleanup expired OTPs every hour
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupExpiredOtps() {
        log.info("Starting OTP cleanup job");
        otpService.cleanupExpiredOtps();
        log.info("OTP cleanup job completed");
    }
}
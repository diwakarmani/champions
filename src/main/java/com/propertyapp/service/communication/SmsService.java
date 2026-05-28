package com.propertyapp.service.communication;

import com.propertyapp.config.TwilioConfig;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {
    
    private final TwilioConfig twilioConfig;
    
    /**
     * Send OTP via SMS using Twilio
     */
    @Async
    public void sendOtpSms(String mobile, String otpCode) {
        try {
            // Initialize Twilio
            Twilio.init(twilioConfig.getAccountSid(), twilioConfig.getAuthToken());
            
            String messageBody = String.format(
                "Your OTP code is: %s. Valid for 10 minutes. Do not share this code.",
                otpCode
            );
            
            Message message = Message.creator(
                new PhoneNumber(mobile),
                new PhoneNumber(twilioConfig.getFromNumber()),
                messageBody
            ).create();
            
            log.info("SMS sent successfully. SID: {}", message.getSid());

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobile, e.getMessage());
        }
    }
    
    /**
     * Send custom SMS
     */
    @Async
    public void sendSms(String mobile, String message) {
        try {
            Twilio.init(twilioConfig.getAccountSid(), twilioConfig.getAuthToken());
            
            Message msg = Message.creator(
                new PhoneNumber(mobile),
                new PhoneNumber(twilioConfig.getFromNumber()),
                message
            ).create();
            
            log.info("SMS sent successfully. SID: {}", msg.getSid());

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobile, e.getMessage());
        }
    }
}
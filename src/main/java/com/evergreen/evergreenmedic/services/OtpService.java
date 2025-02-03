package com.evergreen.evergreenmedic.services;

import com.evergreen.evergreenmedic.dtos.requests.otp.SenOtpReqDto;
import com.evergreen.evergreenmedic.entities.OtpEntity;
import com.evergreen.evergreenmedic.enums.OtpType;
import com.evergreen.evergreenmedic.enums.OtpValidFor;
import com.evergreen.evergreenmedic.repositories.OtpRepository;
import com.evergreen.evergreenmedic.repositories.UserRepository;
import com.evergreen.evergreenmedic.services.smtp.EmailService;
import com.evergreen.evergreenmedic.smtp.EmailDetails;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class OtpService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final EmailService emailService;

    public OtpService(UserRepository userRepository, OtpRepository otpRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    private boolean isOtpExpired(OtpEntity lastOtp) {
        long secondsSinceLastOtp = Duration.between(lastOtp.getCreatedAt(), Instant.now()).getSeconds();
        return secondsSinceLastOtp >= lastOtp.getExpirationTime();
    }

    private EmailDetails createKycOtpEmail(String email, OtpValidFor otpValidFor, Integer otpCode) {
        String messageBody = "Dear EvergreenMedic User,\n\n" +
                "Your request to verify your email address for KYC (Know Your Customer) purposes has been received. " +
                "To complete the verification process, please enter the following verification code: " + otpCode + " in the EvergreenMedic app.\n\n" +
                "For any issues or assistance, please contact us at 3737 (For EvergreenMedic Users) or 042-111-00-3737 (For Other Networks).\n\n" +
                "Kind Regards,\n" +
                "EvergreenMedic Team";

        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSubject("KYC EMAIL VERIFICATION OTP");
        emailDetails.setRecipient(email);
        emailDetails.setMsgBody(messageBody);

        return emailDetails;
    }

    private EmailDetails createOtpEmail(String email, OtpValidFor otpValidFor, Integer otpCode) {
        String messageBody = "Dear EvergreenMedic User, your request to register your email address account has been received. To verify, please enter the verification code: "
                + otpCode + " in the EvergreenMedic app.\n" +
                "For any issues, please contact us on 3737 (For EvergreenMedic Users) and 042-111-00-3737 (For Other Networks).\n\n";
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSubject("REGISTRATION OTP");
        emailDetails.setRecipient(email);
        emailDetails.setMsgBody(messageBody);
        return emailDetails;
    }

    public boolean sendEmailOtp(SenOtpReqDto senOtpReqDto) throws BadRequestException {
        String email = senOtpReqDto.getEmail();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists.");
        }

        // Check for the last OTP expiration
        OtpEntity lastOtp = otpRepository.findByOtpEmail(email).orElse(null);
        if (lastOtp != null && !isOtpExpired(lastOtp)) {
            throw new BadRequestException("Please wait for the last OTP to expire before requesting a new one.");
        }

        OtpEntity newOtpEntity = new OtpEntity();
        newOtpEntity.setOtpEmail(email);
        newOtpEntity.setOtpType(OtpType.EMAIL);
        Integer otpCode = 111111;
        newOtpEntity.setCode(otpCode);
        otpRepository.save(newOtpEntity);

        EmailDetails emailDetails = null;
        if (senOtpReqDto.getOtpValidFor() == OtpValidFor.KYC) {
            emailDetails = createKycOtpEmail(email, senOtpReqDto.getOtpValidFor(), otpCode);
            return emailService.sendSimpleMail(emailDetails);

        } else if (senOtpReqDto.getOtpValidFor() == OtpValidFor.REGISTER) {
            emailDetails = createOtpEmail(email, senOtpReqDto.getOtpValidFor(), otpCode);
            return emailService.sendSimpleMail(emailDetails);
        } else {
            return false;

        }

    }
}

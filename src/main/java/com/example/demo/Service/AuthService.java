package com.example.demo.Service;

import com.example.demo.Exception.Auth.alreadyRegisteredException;
import com.example.demo.dto.auth.*;
import com.example.demo.dto.jwt.TokenDto;
import com.example.demo.dto.jwt.TokenReqDto;
import com.example.demo.dto.member.MemberResponseDto;
import com.example.demo.entity.Member;
import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.TokenProvider;
import com.example.demo.repository.MemberRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.VerifySmsRepository;
import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Random;

@Configuration
@Component
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final AuthenticationManagerBuilder managerBuilder;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsCertificationDao;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProvider tokenProvider;
    private final VerifySmsRepository verifySmsRepository;
    private final CustomUserDetailsService customUserDetailsService;
    private final NicknameGenerator nicknameGenerator;
    private static DefaultMessageService messageService ;

    @Value("${sms.apiKey}")
    private String apiKey;
    @Value("${sms.secretKey}")
    private String apiSecret;



    @PostConstruct
    public void init() {
        messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }

    public MemberResponseDto signup(MemberRequestDto requestDto) {
        if (memberRepository.existsByEmail(requestDto.getEmail())) {
            throw new DuplicateKeyException("?????? ???????????? ?????? ???????????????");
        }

        Member member = requestDto.toMember(passwordEncoder);
        if(smsCertificationDao.hasKey(member.getPhonenumber())) {
            // ??????????????? ??????????????? ???????????? ??????
            String Origincert = smsCertificationDao.getSmsCertification(member.getPhonenumber());
            String[] split = Origincert.split(":");
            if(split[1].equals("01")){
                smsCertificationDao.removeSmsCertification(member.getPhonenumber());
                member.setNickname(nicknameGenerator.generateRandomNickname());
                return MemberResponseDto.of(memberRepository.save(member));
            }
        }else{
            throw new RuntimeException("????????????????????????");
        }
        return MemberResponseDto.of(null);
    }

    public TokenDto login(LoginDto loginrequest) {
        UsernamePasswordAuthenticationToken authenticationToken = loginrequest.toAuthentication();
        Authentication authentication = managerBuilder.getObject().authenticate(authenticationToken);
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);
        RefreshToken refreshToken = RefreshToken.builder()
                .email(loginrequest.getEmail())
                .value(tokenDto.getRefreshToken())
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenDto;
    }

    @Transactional
    public TokenDto reissue(TokenReqDto tokenRequestDto) {
        /*
         *  accessToken ??? JWT Filter ?????? ???????????? ???
         * */
        String originAccessToken = tokenRequestDto.getAccessToken();
        String originRefreshToken = tokenRequestDto.getRefreshToken();

        // refreshToken ??????
        Boolean refreshTokenFlag = tokenProvider.validateRefreshToken(originRefreshToken);
        if (refreshTokenFlag == false) {
            throw new RuntimeException("refreshToken ??? ?????????????????????");
        }

        // 2. Access Token ?????? Member Email ????????????
        Authentication authentication = tokenProvider.getAuthentication(originAccessToken);
        Member authMember = memberRepository.findById(Long.parseLong(authentication.getName())).orElseThrow(() -> new RuntimeException("????????? ?????? ????????? ????????????"));

        // 3. ??????????????? Member Email ??? ???????????? Refresh Token ??? ?????????
        RefreshToken refreshToken = refreshTokenRepository.findByEmail(authMember.getEmail())
                .orElseThrow(() -> new RuntimeException("??????????????? ????????? ?????????"));

        // 4. Refresh Token ??????????????? ??????
        if (!refreshToken.getValue().equals(originRefreshToken)) {
            throw new RuntimeException("refreshToken ??? ???????????? ????????????");
        }

        Authentication newAuthentication = tokenProvider.getAuthentication(originAccessToken);
        TokenDto tokenDto= tokenProvider.generateTokenDto(newAuthentication);

        // 6. ????????? ?????? ???????????? (dirtyChecking?????? ????????????)
        refreshToken.updateValue(tokenDto.getRefreshToken());

        // ?????? ??????
        return tokenDto;
    }

    public SmsDto PhoneNumberCheck(String phoneNumber,Boolean isFindID) {
        if(isFindID){
            if(!memberRepository.existsByPhonenumber(phoneNumber)){
                throw new alreadyRegisteredException("???????????? ?????? ?????? ???????????????");
            }
        }
        Random rand  = new Random();
        String numStr = "";
        for(int i=0; i<4; i++) {
            String ran = Integer.toString(rand.nextInt(10));
            numStr+=ran;
        }
        Message coolsms = new Message();
        coolsms.setFrom("01046306320");
        coolsms.setTo(phoneNumber);
        coolsms.setText("[ssopa]??????????????? [" + numStr + "] ?????????.");
        SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(coolsms));
        System.out.println(response);
        smsCertificationDao.createSmsCertification(phoneNumber,numStr+":0"); //??????

        return new SmsDto().builder()
                .success(true)
                .build();
    }

    //???????????? ????????? ??????????????? Redis??? ????????? ??????????????? ???????????? ??????
    public SmsDto verifySms(String certNumber,String phoneNumber) {
        if (isVerify(certNumber+":0",phoneNumber)) {
            return new SmsDto().builder()
                    .success(false)
                    .build();
        }else{
            System.out.println("???????????? ??????");
            smsCertificationDao.check_as_verfied(phoneNumber);
        }
        return new SmsDto().builder()
                .success(true)
                .build();
    }

    private boolean isVerify(String certNumber,String phoneNumber) {
        return !(smsCertificationDao.hasKey(phoneNumber) &&
                smsCertificationDao.getSmsCertification(phoneNumber)
                        .equals(certNumber));
    }

    public boolean findID(String name, String phonenumber) {
        if(memberRepository.findByNameAndPhonenumber(name,phonenumber) != null) {
            PhoneNumberCheck(phonenumber,true);
            return true;
        }else{
            return false;
        }
    }

    public FindIdResponseDto findIdverifySms(String certNumber, String phoneNumber) {
        if (isVerify(certNumber+":0",phoneNumber)) {
            throw new RuntimeException("??????????????? ???????????? ????????????.");
        }else{
            System.out.println("???????????? ??????");
            smsCertificationDao.removeSmsCertification(phoneNumber);

            Member member = memberRepository.findByPhonenumber(phoneNumber).orElseThrow(() -> new RuntimeException("????????? ?????? ????????? ????????????"));
            System.out.println("????????? ?????? : "+member.getEmail());

            return new FindIdResponseDto().builder()
                    .email(member.getEmail())
                    .build();
        }
    }
    public SuccessDto resetPassword(String email, String password, String passwordConfirm) {
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("????????? ?????? ????????? ????????????"));

        if(!password.equals(passwordConfirm)){
            throw new RuntimeException("??????????????? ???????????? ????????????.");
        }
        member.setPassword(passwordEncoder.encode(password));
        memberRepository.save(member);
        return new SuccessDto().builder()
                .success(true)
                .build();
    }

    public CheckDuplicateEmailResponseDto checkDuplicatedEmail(String email) {
        if(memberRepository.existsByEmail(email)) {
            return new CheckDuplicateEmailResponseDto().builder()
                    .isDuplicated(true)
                    .build();
        }else{
            return new CheckDuplicateEmailResponseDto().builder()
                    .isDuplicated(false)
                    .build();
        }
    }

    public CheckDuplicateNumberResponse checkDuplicatedNumber(String number) {
        if(memberRepository.existsByPhonenumber(number)){
            return new CheckDuplicateNumberResponse().builder()
                    .isDuplicated(true)
                    .build();
        }else{
            return new CheckDuplicateNumberResponse().builder()
                    .isDuplicated(false)
                    .build();
        }
    }

    public getNicknameResponse getNickname(String email) {
        Optional<Member> member = memberRepository.findByEmail(email);
        member.ifPresentOrElse(m -> {

        }, () -> {
            throw new RuntimeException("getNickname ??????");
        });

        return new getNicknameResponse().builder()
                .nickname(member.get().getNickname())
                .build();
    }
}
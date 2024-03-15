package com.example.bootshelf.user.service;


import com.example.bootshelf.admin.model.request.PostSignUpAdminReq;
import com.example.bootshelf.admin.model.response.PostSignUpAdminRes;
import com.example.bootshelf.certification.Certification;
import com.example.bootshelf.certification.repository.CertificationRepository;
import com.example.bootshelf.common.BaseRes;
import com.example.bootshelf.common.error.ErrorCode;
import com.example.bootshelf.common.error.entityexception.AdminException;
import com.example.bootshelf.config.aws.ImageUtils;
import com.example.bootshelf.config.aws.S3Service;
import com.example.bootshelf.config.utils.JwtUtils;
import com.example.bootshelf.course.Course;
import com.example.bootshelf.common.error.entityexception.CourseException;
import com.example.bootshelf.course.repository.CourseRepository;
import com.example.bootshelf.common.error.entityexception.UserException;
import com.example.bootshelf.user.model.entity.User;
import com.example.bootshelf.user.model.request.PatchUpdateUserReq;
import com.example.bootshelf.user.model.request.PostCheckPasswordReq;
import com.example.bootshelf.user.model.request.PostLoginUserReq;
import com.example.bootshelf.user.model.request.PostSignUpUserReq;
import com.example.bootshelf.user.model.response.*;
import com.example.bootshelf.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    @Value("${jwt.secret-key}")
    private String secretKey;
    @Value("${jwt.token.expired-time-ms}")
    private Long expiredTimeMs;
    @Value("${cloud.aws.s3.profile-bucket}")
    private String profileBucket;

    private final S3Service s3Service;
    private final UserRepository userRepository;
    private final CertificationRepository certificationRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender emailSender;
    private final EmailVerifyService emailVerifyService;
    private final JwtUtils jwtUtils;

    @Transactional(readOnly = false)
    public User saveUser(PostSignUpUserReq postSignUpUserReq, MultipartFile profileImage) {

        String savePath;

        if (profileImage == null || profileImage.isEmpty()) {
            // 프로필 이미지가 없는 경우 기본 이미지 경로를 설정
            savePath = "https://bootshelf-profile.s3.ap-northeast-2.amazonaws.com/2024/03/14/6a0ac29b-55c8-4fd0-808a-fcd1b9deda76_default.png";
        } else {
            // 프로필 이미지가 있는 경우 S3에 업로드
            savePath = ImageUtils.makeBoardImagePath(profileImage.getOriginalFilename());
            savePath = s3Service.uploadBoardFile(profileBucket, profileImage, savePath);
        }

        User user = User.builder()
                .password(passwordEncoder.encode(postSignUpUserReq.getPassword()))
                .name(postSignUpUserReq.getName())
                .email(postSignUpUserReq.getEmail())
                .nickName(postSignUpUserReq.getNickName())
                .profileImage(savePath)
                .authority("ROLE_USER")
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .status(false)
                .build();

        userRepository.save(user);

        return user;
    }

    // 회원가입
    @Transactional(readOnly = false)
    public BaseRes signup(PostSignUpUserReq postSignUpUserReq, MultipartFile profileImage) {
        Optional<User> resultEmail = userRepository.findByEmail(postSignUpUserReq.getEmail());
        Optional<User> resultUserNickName = userRepository.findByNickName(postSignUpUserReq.getNickName());

        // 중복된 이메일에 대한 예외처리
        if (resultEmail.isPresent()) {
            throw new UserException(ErrorCode.DUPLICATE_SIGNUP_EMAIL, String.format("SignUp Email [ %s ] is duplicated.", postSignUpUserReq.getEmail()));
        }
        // 중복된 닉네임에 대한 예외처리
        if (resultUserNickName.isPresent()) {
            throw new UserException(ErrorCode.DUPLICATE_USER_NICKNAME, String.format("SignUp NickName [ %s ] is duplicated.", postSignUpUserReq.getNickName()));
        }

        if (postSignUpUserReq.getProgramName() == null) {
            User user = saveUser(postSignUpUserReq, profileImage);

            BaseRes baseRes = BaseRes.builder()
                    .isSuccess(true)
                    .message("회원가입에 성공하였습니다.")
                    .result(PostSignUpUserRes.builder()
                            .userEmail(user.getEmail())
                            .userName(user.getName())
                            .build())
                    .build();

            return baseRes;
        } else {
            Optional<Course> resultCourse = courseRepository.findByProgramName(postSignUpUserReq.getProgramName());
            // 과정명이 존재하지 않을 때 예외처리
            if (!resultCourse.isPresent()) {
                throw new CourseException(ErrorCode.COURSE_NOT_EXISTS, String.format("Course [%s] is not exists.", postSignUpUserReq.getProgramName()));
            }

            User user = saveUser(postSignUpUserReq, profileImage);

            Certification certification = Certification.builder()
                    .user(user)
                    .course(Course.builder().idx(resultCourse.get().getIdx()).build())
                    .status(true)
                    .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                    .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                    .build();

            certificationRepository.save(certification);

            user.setAuthority("ROLE_AUTHUSER");
            userRepository.save(user);
            BaseRes baseRes = BaseRes.builder()
                    .isSuccess(true)
                    .message("인증회원 가입에 성공하였습니다.")
                    .result(PostSignUpUserRes.builder()
                            .userEmail(user.getEmail())
                            .userName(user.getName())
                            .build())
                    .build();

            return baseRes;
        }
    }

    @Transactional(readOnly = true)
    public BaseRes list(Integer page, Integer size) {

        Pageable pageable = PageRequest.of(page - 1, size);

        Page<User> userList = userRepository.findUserList(pageable);

        List<GetListUserRes> getListUserResList = new ArrayList<>();
        for (User user : userList) {

            GetListUserRes getListUserRes = GetListUserRes.builder()
                    .userIdx(user.getIdx())
                    .email(user.getEmail())
                    .name(user.getName())
                    .build();

            getListUserResList.add(getListUserRes);
        }

        return BaseRes.builder()
                .isSuccess(true)
                .message("요청 성공")
                .result(getListUserResList)
                .build();
    }

    @Transactional(readOnly = true)
    public BaseRes read(String email) {
        Optional<User> result = userRepository.findUser(email);

        if (result.isPresent()) {
            User user = result.get();

            GetListUserRes getListUserRes = GetListUserRes.builder()
                    .userIdx(user.getIdx())
                    .email(user.getEmail())
                    .name(user.getName())
                    .nickName(user.getNickName())
                    .profileImage(user.getProfileImage())
                    .build();

            return BaseRes.builder()
                    .isSuccess(true)
                    .message("요청 성공")
                    .result(getListUserRes)
                    .build();
        } else {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("User email [ %s ] is not exists.", email));
        }
    }

    // 회원 로그인
    @Transactional(readOnly = false)
    public BaseRes login(PostLoginUserReq postLoginUserReq) {
        Optional<User> result = userRepository.findByEmail(postLoginUserReq.getEmail());

        if (result.isEmpty()) {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("User email [ %s ] is not exists.", postLoginUserReq.getEmail()));
        }

        User user = result.get();
        if (passwordEncoder.matches(postLoginUserReq.getPassword(), user.getPassword()) && user.getStatus().equals(true)) {
            PostLoginUserRes postLogInUserRes = PostLoginUserRes.builder()
                    .token(jwtUtils.generateAccessToken(user, secretKey, expiredTimeMs))
                    .build();

            return BaseRes.builder()
                    .isSuccess(true)
                    .message("로그인에 성공하였습니다.")
                    .result(postLogInUserRes)
                    .build();
        } else {
            throw new UserException(ErrorCode.DIFFERENT_USER_PASSWORD, String.format("User Password [ %s ] is different.", postLoginUserReq.getPassword()));
        }
    }

    // 회원정보 수정을 위한 비밀번호 체크
    @Transactional
    public Boolean checkPassword(User user, PostCheckPasswordReq postCheckPasswordReq) {
        if (passwordEncoder.matches(postCheckPasswordReq.getPassword(), user.getPassword())) {
            return true;
        } else {
            return false;
        }
    }

    // 인증메일 발송
    @Transactional(readOnly = false)
    public void sendEmail(PostSignUpUserReq postSignUpUserReq) {
        MimeMessage message = emailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true는 멀티파트 메시지를 사용함을 의미합니다.
            helper.setTo(postSignUpUserReq.getEmail());
            helper.setSubject("[BootShelf] 회원가입을 완료하기 위해서 이메일 인증을 진행해 주세요");
            String uuid = UUID.randomUUID().toString();
            String url = "http://localhost:8080/user/verify?email=" + postSignUpUserReq.getEmail() + "&uuid=" + uuid;

            // 이미지 파일 경로
            String imagePath = "https://github.com/hyungdoyou/devops/assets/148875644/f9dc322f-9d41-455d-b35c-e3cfcd7c008d";

            // HTML 문자열에 이미지 포함
            String content = "<html><body style= 'font-family: Arial, sans-serif;'>" +
                    "<img src='" + imagePath + "' style='width: auto; height: auto;'/>" +
                    "<p style='color: rgb(84, 29, 122); margin-bottom: 15px; height: 100%; margin: 0; text-align: center; font-size: 30px; line-height: 3;'>" +
                    "<strong>BOOTSHELF</strong> 에 가입해주셔서 감사합니다" +
                    "</p>" +
                    "<p style='color: #333; margin-bottom: 15px; height: 100%; margin: 0; text-align: center; font-size: 20px; line-height: 3;'>" +
                    "이메일 인증 완료 후 회원들과 지식을 공유해보세요" +
                    "</p>" +
                    "<div style='text-align: center;'>\n" +
                    "    <a href='" + url + "' style='color: #fff; text-decoration: none; background-color: rgb(84, 29, 122); padding: 10px 20px; border-radius: 5px; border: 2px solid rgb(84, 29, 122); display: inline-block; font-size: 15px; line-height: 2;'>\n" +
                    "        이메일 인증하기\n" +
                    "    </a>\n" +
                    "</div>" +
                    "</body></html>";
            helper.setText(content, true); // true는 HTML 메일임을 의미합니다.
            emailSender.send(message);
            emailVerifyService.create(postSignUpUserReq.getEmail(), uuid);
        } catch (MessagingException e) {
            e.printStackTrace();
            // 예외 처리 로직
        }
    }

    // 메일 인증 완료 후 회원 상태 수정
    @Transactional(readOnly = false)
    public BaseRes updateStatus(String email) {
        Optional<User> result = userRepository.findByEmail(email);
        if (result.isPresent()) {
            User user = result.get();
            user.setStatus(true);
            userRepository.save(user);

            return BaseRes.builder()
                    .isSuccess(true)
                    .message("메일 인증에 성공하였습니다.")
                    .result(GetEmailVerifyRes.builder()
                            .email(user.getEmail())
                            .status(user.getStatus())
                            .build())
                    .build();
        } else {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("User email [ %s ] is not exists.", email));
        }
    }

    // 카카오 회원가입
    @Transactional
    public void kakaoSignup(String nickName, String profileImage) {

        User user = User.builder()
                .email(nickName + "@kakao.com")
                .password(passwordEncoder.encode("kakao"))
                .nickName(nickName)
                .name(nickName)
                .profileImage("https://bootshelf-profile.s3.ap-northeast-2.amazonaws.com/2024/03/14/6a0ac29b-55c8-4fd0-808a-fcd1b9deda76_default.png")
                .authority("ROLE_KAKAO")
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .status(true)
                .build();

        user = userRepository.save(user);

    }

    // 회원 이메일 검증
    public User getUserEmail(String email) {
        Optional<User> result = userRepository.findByEmail(email);

        if (result.isPresent()) {
            return result.get();
        } else {
            return null;  // 카카오 로그인 시 null이 반환되면 가입 진행토록 설정
        }
    }

    // 회원 닉네임, 패스워드 수정
    @Transactional(readOnly = false)
    public BaseRes update(String userEmail, PatchUpdateUserReq patchUpdateUserReq) {

        // 중복된 닉네임에 대한 예외처리
        if (patchUpdateUserReq.getNickName() != null) {
            Optional<User> resultUserNickName = userRepository.findByNickName(patchUpdateUserReq.getNickName());

            if (resultUserNickName.isPresent()) {
                throw new UserException(ErrorCode.DUPLICATE_USER_NICKNAME, String.format("Updated NickName [ %s ] is duplicated.", patchUpdateUserReq.getNickName()));
            }
        }

        // 사용자를 못찾을 때 예외처리
        Optional<User> result = userRepository.findByEmail(userEmail);

        if (!result.isPresent()) {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("UserEmail [ %s ] is not exists.", userEmail));
        }

        User user = result.get();

        if (patchUpdateUserReq.getPassword() != null) {
            if (!patchUpdateUserReq.getPassword().equals(patchUpdateUserReq.getCheckPassword())) {
                throw new UserException(ErrorCode.DIFFERENT_USER_PASSWORD, String.format("Password [ %s ] is different with [ %s ].", patchUpdateUserReq.getPassword(), patchUpdateUserReq.getCheckPassword()));
            }
            user.update(patchUpdateUserReq, passwordEncoder.encode(patchUpdateUserReq.getPassword()));
        } else {
            user.update(patchUpdateUserReq, null);
        }

        user.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        userRepository.save(user);

        return BaseRes.builder()
                .isSuccess(true)
                .message("회원정보 수정 성공")
                .result("요청 성공")
                .build();
    }

    // 회원 프로필 이미지 수정
    public BaseRes updateImage(String userEmail, MultipartFile profileImage) {
        // 사용자를 못찾을 때 예외처리
        Optional<User> result = userRepository.findByEmail(userEmail);

        if (!result.isPresent()) {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("UserEmail [ %s ] is not exists.", userEmail));
        }

        User user = result.get();
        // 프로필 이미지가 있는 경우 S3에 업로드
        String savePath = ImageUtils.makeBoardImagePath(profileImage.getOriginalFilename());
        savePath = s3Service.uploadBoardFile(profileBucket, profileImage, savePath);

        user.setProfileImage(savePath);
        user.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        userRepository.save(user);

        BaseRes baseRes = BaseRes.builder()
                .isSuccess(true)
                .message("프로필 이미지 수정 성공")
                .result("요청 성공")
                .build();
        return baseRes;
    }


    @Transactional(readOnly = false)
    public BaseRes cancel(Integer userIdx) {

        Optional<User> byUserIdx = userRepository.findByIdx(userIdx);
        if (byUserIdx.isPresent()) {
            User loginUser = byUserIdx.get();
            loginUser.setStatus(false);
            userRepository.save(loginUser);

            return BaseRes.builder()
                    .isSuccess(true)
                    .message("요청 성공")
                    .result("회원의 상태가 탈퇴 상태로 변경되었습니다.")
                    .build();
        } else {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("UserIdx [ %s ] is not exists.", userIdx));
        }
    }

    @Transactional(readOnly = false)
    public BaseRes delete(Integer userIdx) {

        Integer result = userRepository.deleteByIdx(userIdx);
        if (!result.equals(0)) {

            return BaseRes.builder()
                    .isSuccess(true)
                    .message("요청 성공")
                    .result("회원이 삭제되었습니다.")
                    .build();
        } else {
            throw new UserException(ErrorCode.USER_NOT_EXISTS, String.format("UserIdx [ %s ] is not exists.", userIdx));
        }
    }

    // 관리자 회원가입
    @Transactional(readOnly = false)
    public BaseRes adminSignup(PostSignUpAdminReq postSignUpAdminReq) {
        Optional<User> resultEmail = userRepository.findByEmail(postSignUpAdminReq.getEmail());

        // 중복된 이메일에 대한 예외처리
        if (resultEmail.isPresent()) {
            throw new AdminException(ErrorCode.DUPLICATE_SIGNUP_EMAIL, String.format("SignUp Email [ %s ] is duplicated.", postSignUpAdminReq.getEmail()));
        }

        User user = User.builder()
                .password(passwordEncoder.encode(postSignUpAdminReq.getPassword()))
                .name(postSignUpAdminReq.getName())
                .email(postSignUpAdminReq.getEmail())
                .authority("ROLE_ADMIN")
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .status(true)
                .build();

        userRepository.save(user);

        BaseRes baseRes = BaseRes.builder()
                .isSuccess(true)
                .message("관리자 가입에 성공하였습니다.")
                .result(PostSignUpAdminRes.builder()
                        .email(user.getEmail())
                        .name(user.getName())
                        .build())
                .build();

        return baseRes;
    }
}
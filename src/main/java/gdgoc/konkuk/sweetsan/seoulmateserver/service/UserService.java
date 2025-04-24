package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자의 정보를 조회합니다.
     * @param email 사용자의 이메일
     * @return UserInfoDto 사용자 정보
     */
    public UserInfoDto getUserInfo(String email) {
        User user = getUserByEmail(email);
        return UserInfoDto.builder()
                .name(user.getName())
                .birthYear(user.getBirthYear())
                .build();
    }

    /**
     * 사용자의 정보를 업데이트합니다.
     * @param email 사용자의 이메일
     * @param userInfoDto 업데이트할 사용자 정보
     * @return UserInfoDto 업데이트된 사용자 정보
     */
    public UserInfoDto updateUserInfo(String email, UserInfoDto userInfoDto) {
        User user = getUserByEmail(email);
        
        user.setName(userInfoDto.getName());
        user.setBirthYear(userInfoDto.getBirthYear());
        
        User savedUser = userRepository.save(user);
        
        return UserInfoDto.builder()
                .name(savedUser.getName())
                .birthYear(savedUser.getBirthYear())
                .build();
    }
    
    /**
     * 이메일로 사용자를 조회합니다.
     * @param email 사용자의 이메일
     * @return User 사용자 엔티티
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}

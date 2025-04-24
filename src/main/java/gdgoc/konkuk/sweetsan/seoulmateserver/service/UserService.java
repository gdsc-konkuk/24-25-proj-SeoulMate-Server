package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing user information.
 * Provides methods for retrieving and updating user details.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Retrieves user information.
     * 
     * @param email the user's email
     * @return UserInfoDto containing the user's information
     * @throws ResourceNotFoundException if the user is not found
     */
    public UserInfoDto getUserInfo(String email) {
        User user = getUserByEmail(email);
        return UserInfoDto.builder()
                .name(user.getName())
                .birthYear(user.getBirthYear())
                .build();
    }

    /**
     * Updates a user's information.
     * 
     * @param email the user's email
     * @param userInfoDto the updated user information
     * @return UserInfoDto containing the updated user information
     * @throws ResourceNotFoundException if the user is not found
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
     * Retrieves a user by their email address.
     * 
     * @param email the user's email
     * @return User entity
     * @throws ResourceNotFoundException if the user is not found
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}

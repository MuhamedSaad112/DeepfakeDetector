package com.deepfakedetector.service.profile;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageUpdateService {

    private final ProfileImageService profileImageService;
    private final UserRepository userRepository;

    public String updateUserProfileImage(String userName, MultipartFile file) throws DeepfakeException {
        // 1) Retrieve the user from DB by userName
        User user = userRepository.findOneByUserName(userName.toLowerCase())
                .orElseThrow(() -> new DeepfakeException(DetectionErrorCode.USER_NOT_FOUND));

        // 2) Store the file on disk
        String storedFileName;
        storedFileName = profileImageService.storeImage(file, userName);

        // 3) Update the imageUrl in the user entity
        user.setImageUrl(storedFileName);

        // 4) Save user
        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to update user profile image URL for {}: {}", userName, e.getMessage(), e);
            throw new DeepfakeException(DetectionErrorCode.GENERAL_ERROR);
        }

        return storedFileName;
    }

    public byte[] loadImage(String userName) throws DeepfakeException {
        return profileImageService.loadImage(userName);
    }
}

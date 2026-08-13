package com.talkie.chat.user.service;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.user.dto.UserSearchResponse;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.enums.PreferredLanguage;
import com.talkie.chat.user.exception.UserErrorCode;
import com.talkie.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User getUser(Long id) {
        return findUser(id);
    }

    @Transactional
    public User updateProfile(Long id, String nickname, String profileImageUrl, PreferredLanguage preferredLanguage) {
        User user = findUser(id);
        user.updateProfile(nickname, profileImageUrl, preferredLanguage);
        return user;
    }

    @Transactional
    public User updatePassword(Long id, String password) {
        User user = findUser(id);

        String encodedPassword = passwordEncoder.encode(password);
        user.updatePassword(encodedPassword);
        return user;
    }

    public List<UserSearchResponse> searchUsers(Long requesterId, String keyword, String cursor, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(UserErrorCode.SEARCH_KEYWORD_REQUIRED);
        }

        return userRepository.searchByNickname(keyword, requesterId, cursor, size).stream()
                .map(UserSearchResponse::from)
                .toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}

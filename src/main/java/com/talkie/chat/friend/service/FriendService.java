package com.talkie.chat.friend.service;

import com.talkie.chat.friend.dto.FriendRequestResponse;
import com.talkie.chat.friend.dto.FriendResponse;
import com.talkie.chat.friend.entity.Friendship;
import com.talkie.chat.friend.enums.FriendshipStatus;
import com.talkie.chat.friend.exception.FriendErrorCode;
import com.talkie.chat.friend.repository.FriendshipRepository;
import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    @Transactional
    public FriendRequestResponse sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new BusinessException(FriendErrorCode.SELF_REQUEST_NOT_ALLOWED);
        }

        User requester = findUser(requesterId);
        User addressee = findUser(addresseeId);

        if (friendshipRepository.existsByRequesterIdAndAddresseeId(requesterId, addresseeId)) {
            throw new BusinessException(FriendErrorCode.FRIENDSHIP_ALREADY_EXISTS);
        }

        Friendship friendship = new Friendship(requester, addressee);
        return FriendRequestResponse.from(friendshipRepository.save(friendship));
    }

    @Transactional
    public void acceptRequest(Long addresseeId, Long friendshipId) {
        Friendship friendship = findFriendship(friendshipId);

        if (!friendship.getAddressee().getId().equals(addresseeId)) {
            throw new BusinessException(FriendErrorCode.NOT_REQUEST_ADDRESSEE);
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new BusinessException(FriendErrorCode.FRIENDSHIP_NOT_PENDING);
        }

        friendship.accept();

        friendshipRepository.findByRequesterIdAndAddresseeId(addresseeId, friendship.getRequester().getId())
                .ifPresent(friendshipRepository::delete);
    }

    @Transactional
    public void removeRequest(Long userId, Long friendshipId) {
        Friendship friendship = findFriendship(friendshipId);

        boolean isParticipant = friendship.getRequester().getId().equals(userId)
                || friendship.getAddressee().getId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(FriendErrorCode.NOT_FRIENDSHIP_PARTICIPANT);
        }

        friendshipRepository.delete(friendship);
    }

    public List<FriendRequestResponse> getReceivedRequests(Long userId) {
        return friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(FriendRequestResponse::from)
                .toList();
    }

    public List<FriendResponse> getFriends(Long userId) {
        return friendshipRepository.findAcceptedByUserId(userId).stream()
                .map(friendship -> friendship.getRequester().getId().equals(userId)
                        ? friendship.getAddressee()
                        : friendship.getRequester())
                .map(FriendResponse::from)
                .toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.USER_NOT_FOUND));
    }

    private Friendship findFriendship(Long id) {
        return friendshipRepository.findById(id)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIENDSHIP_NOT_FOUND));
    }
}
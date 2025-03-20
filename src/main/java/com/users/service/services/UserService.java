package com.users.service.services;

import com.users.service.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserRegistrationResp registerUser(UserRegistrationDTO request);
    UserLoginResp loginUser(UserLoginReq request);
    UserResp getUserById(String id);
    Page<UserResp> getAllUsersByIds(List<String> ids, Pageable pageable);
    Page<UserResp> getAllUsers(Pageable pageable);
}

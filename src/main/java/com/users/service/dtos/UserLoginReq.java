package com.users.service.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserLoginReq {
    private String username;
    private String password;
}

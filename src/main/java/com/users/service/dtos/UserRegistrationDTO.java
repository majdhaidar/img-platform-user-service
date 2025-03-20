package com.users.service.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegistrationDTO {
    private String username;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String email;
    private String password;
}

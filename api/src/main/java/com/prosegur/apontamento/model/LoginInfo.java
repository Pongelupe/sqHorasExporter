package com.prosegur.apontamento.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@ToString(exclude = "password")
@AllArgsConstructor
@NoArgsConstructor
public class LoginInfo {

    String username;
    String password;

}

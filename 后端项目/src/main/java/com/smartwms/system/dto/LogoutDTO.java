package com.smartwms.system.dto;

import lombok.Data;

/** 登出入参（API-AUTH-003）：refreshToken 可空；登出幂等，重复登出返回 200。 */
@Data
public class LogoutDTO {

    private String refreshToken;
}

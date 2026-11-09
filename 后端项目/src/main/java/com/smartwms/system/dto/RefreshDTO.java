package com.smartwms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新令牌入参（API-AUTH-002）。 */
@Data
public class RefreshDTO {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}

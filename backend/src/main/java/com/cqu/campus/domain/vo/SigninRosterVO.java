package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 签到名单行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigninRosterVO {

    private Long signupId;
    private String studentNo;
    private String studentName;
    private String signupStatus;
    private boolean signed;
    private String source;
    private String supplementStatus;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime signedAt;
}

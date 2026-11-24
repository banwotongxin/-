package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String studentNo;
    private String status;
    private List<String> roles;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}

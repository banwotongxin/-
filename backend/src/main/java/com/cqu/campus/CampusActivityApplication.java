package com.cqu.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园活动报名与社团信息管理系统启动类
 */
@SpringBootApplication
@MapperScan("com.cqu.campus.mapper")
public class CampusActivityApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusActivityApplication.class, args);
    }
}

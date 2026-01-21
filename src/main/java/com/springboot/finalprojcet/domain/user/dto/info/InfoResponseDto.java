package com.springboot.finalprojcet.domain.user.dto.info;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InfoResponseDto {
    private Long id;
    private String username;
    private String email;
}

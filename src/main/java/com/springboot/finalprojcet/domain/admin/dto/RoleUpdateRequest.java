package com.springboot.finalprojcet.domain.admin.dto;

import com.springboot.finalprojcet.enums.RoleType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {
    private RoleType roleType;
}

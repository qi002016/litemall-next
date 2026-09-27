package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "地址实体")
public class AddressDTO {

    @Schema(description = "收货人姓名")
    @NotBlank(message = "收货人姓名不能为空")
    private String name;

    @Schema(description = "收货人电话")
    @NotBlank(message = "收货人电话不能为空")
    private String tel;

    @Schema(description = "省")
    @NotBlank(message = "省不能为空")
    private String province;

    @Schema(description = "市")
    @NotBlank(message = "市不能为空")
    private String city;

    @Schema(description = "区")
    @NotBlank(message = "区不能为空")
    private String county;

    @Schema(description = "详细地址")
    @NotBlank(message = "详细地址不能为空")
    private String addressDetail;

    @Schema(description = "是否默认")
    private Boolean isDefault;
}

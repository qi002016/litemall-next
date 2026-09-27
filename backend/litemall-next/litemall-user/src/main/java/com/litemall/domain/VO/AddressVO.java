package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "地址实体")
public class AddressVO {

    @Schema(description = "地址 ID")
    private Integer id;

    @Schema(description = "收货人姓名")
    private String name;

    @Schema(description = "收货人电话")
    private String tel;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区/县")
    private String county;

    @Schema(description = "详细地址")
    private String addressDetail;

    @Schema(description = "是否默认")
    private Boolean isDefault;
}

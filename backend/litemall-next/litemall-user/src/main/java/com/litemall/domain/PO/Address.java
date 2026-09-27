package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * <p>
 * 收货地址表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-21
 */
@Data
@TableName("litemall_address")
public class Address implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 收货人名称
     */
    @TableField("name")
    private String name;

    /**
     * 用户表的用户ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 行政区域表的省ID
     */
    @TableField("province")
    private String province;

    /**
     * 行政区域表的市ID
     */
    @TableField("city")
    private String city;

    /**
     * 行政区域表的区县ID
     */
    @TableField("county")
    private String county;

    /**
     * 详细收货地址
     */
    @TableField("address_detail")
    private String addressDetail;

    /**
     * 地区编码
     */
    @TableField("area_code")
    private String areaCode;

    /**
     * 邮政编码
     */
    @TableField("postal_code")
    private String postalCode;

    /**
     * 手机号码
     */
    @TableField("tel")
    private String tel;

    /**
     * 是否默认地址
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 创建时间
     */
    @TableField("add_time")
    private LocalDateTime addTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableField("deleted")
    private Boolean deleted;

}

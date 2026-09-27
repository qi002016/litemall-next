package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.AddressDTO;
import com.litemall.domain.PO.Address;
import com.litemall.domain.VO.AddressVO;
import com.litemall.domain.dto.AddressInfoDTO;

import java.util.List;

public interface IAddressService extends IService<Address> {

    /**
     * 获取地址列表
     */
    List<AddressVO> getAddressList();

    /**
     * 创建地址
     */
    void createAddress(AddressDTO addressDTO);

    /**
     * 更新地址
     */
    AddressDTO updateAddress(Long addressId, AddressDTO addressDTO);
    /**
     * 删除地址
     */
    void deleteAddress(Long addressId);

    /**
     * 设置默认地址
     */

    void setDefaultAddress(Long addressId);

    /**
     * 根据地址ID查询地址信息（内部接口，供订单服务 Feign 调用）
     */
    AddressInfoDTO getAddressInfo(Long addressId);
}

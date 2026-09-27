package com.litemall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.DTO.AddressDTO;
import com.litemall.domain.PO.Address;
import com.litemall.domain.VO.AddressVO;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AddressMapper;
import com.litemall.service.IAddressService;
import com.litemall.utils.BeanUtils;
import com.litemall.utils.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements IAddressService {


    @Override
    public List<AddressVO> getAddressList() {
        Long userId = UserContext.getUser();
        List<Address> addressList = lambdaQuery()
                .eq(Address::getUserId, userId)
                .eq(Address::getDeleted, false)
                .list();
        return addressList.stream().
                map(address -> BeanUtils.copyProperties(address, AddressVO.class)).
                toList();
    }

    @Override
    public void createAddress(AddressDTO addressDTO) {
        Long userId = UserContext.getUser();
        Address address = new Address();
        BeanUtils.copyProperties(addressDTO, address);
        address.setUserId(userId.intValue());
        address.setDeleted(false);
        address.setAddTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());
        save(address);
    }

    @Override
    public AddressDTO updateAddress(Long addressId, AddressDTO addressDTO) {
        Address address = lambdaQuery().eq(Address::getId, addressId).one();
        if (address != null) {
            BeanUtils.copyProperties(addressDTO, address);
            address.setUpdateTime(LocalDateTime.now());
            updateById(address);
        }
        address = lambdaQuery().eq(Address::getId, addressId).one();
        return BeanUtils.copyBean(address, AddressDTO.class);
    }

    @Override
    public void deleteAddress(Long addressId) {
        Address address = lambdaQuery().eq(Address::getId, addressId).one();
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        address.setDeleted(true);
        address.setUpdateTime(LocalDateTime.now());
        updateById(address);
    }

    @Override
    public void setDefaultAddress(Long addressId) {
        Address address = lambdaQuery().eq(Address::getId, addressId).one();
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        address.setIsDefault(true);
        address.setUpdateTime(LocalDateTime.now());
        updateById(address);
    }

    @Override
    public AddressInfoDTO getAddressInfo(Long addressId) {
        Address address = lambdaQuery().eq(Address::getId, addressId)
                .eq(Address::getDeleted, false).one();
        if (address == null) {
            return null;
        }
        AddressInfoDTO dto = new AddressInfoDTO();
        dto.setId(address.getId().longValue());
        dto.setName(address.getName());
        dto.setMobile(address.getTel());
        dto.setProvince(address.getProvince());
        dto.setCity(address.getCity());
        dto.setDistrict(address.getCounty());
        dto.setAddress(address.getAddressDetail());
        dto.setIsDefault(address.getIsDefault());
        return dto;
    }
}

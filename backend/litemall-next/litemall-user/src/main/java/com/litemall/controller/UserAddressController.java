package com.litemall.controller;

import com.litemall.domain.DTO.AddressDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AddressVO;
import com.litemall.service.IAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/address")
@Tag(name = "用户地址", description = "用户地址相关接口")
@RequiredArgsConstructor
public class UserAddressController {

    private final IAddressService addressService;

    @Operation(summary = "获取地址列表")
    @GetMapping
    public Result<List<AddressVO>> getAddressList() {
        List<AddressVO> addressVOList = addressService.getAddressList();
        return Result.ok(addressVOList);
    }

    @Operation(summary = "创建地址")
    @PostMapping("/create")
    public Result createAddress(@RequestBody @Validated AddressDTO addressDTO) {
        addressService.createAddress(addressDTO);
        return Result.ok();
    }

    @Operation(summary = "更新地址")
    @PostMapping("/update")
    public Result<AddressDTO> updateAddress(@RequestBody @Validated AddressDTO addressDTO,
                                            @Validated Long addressId) {
        AddressDTO updatedAddress = addressService.updateAddress(addressId,addressDTO);
        return Result.ok(updatedAddress);
    }

    @Operation(summary = "删除地址")
    @PostMapping("/delete")
    public Result deleteAddress(@RequestBody @Validated AddressDTO addressDTO,
                                @Validated Long addressId) {
        addressService.deleteAddress(addressId);
        return Result.ok();
    }

    @Operation(summary = "设置默认地址")
    @GetMapping("/default")
    public Result setDefaultAddress(@Validated Long addressId) {
        addressService.setDefaultAddress(addressId);
        return Result.ok();
    }
}

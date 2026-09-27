package com.litemall.controller;

import com.litemall.domain.DTO.CartAddDTO;
import com.litemall.domain.DTO.CartUpdateDTO;
import com.litemall.domain.VO.CartItemVO;
import com.litemall.domain.Result;
import com.litemall.service.ICartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/cart")
@Tag(name = "cart", description = "购物车相关接口")
@RequiredArgsConstructor
public class CartController {

    private final ICartService cartService;

    @GetMapping("/list")
    public Result<List<CartItemVO>> list() {
        return Result.ok(cartService.listCart());
    }

    @PostMapping("/add")
    public Result<Map<String, Object>> add(@Validated @RequestBody CartAddDTO dto) {
        return Result.ok(cartService.addCart(dto));
    }

    @PutMapping("/update")
    public Result<CartItemVO> update(@RequestBody CartUpdateDTO dto) {
        return Result.ok(cartService.updateCart(dto));
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long goodsId, @RequestParam Long specId) {
        cartService.deleteCart(goodsId, specId);
        return Result.ok(null);
    }
}

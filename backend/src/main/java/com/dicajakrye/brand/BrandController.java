package com.dicajakrye.brand;

import com.dicajakrye.brand.BrandResponses.BrandDetail;
import com.dicajakrye.brand.BrandResponses.BrandItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    public List<BrandItem> list() {
        return brandService.list();
    }

    @GetMapping("/{slug}")
    public BrandDetail detail(@PathVariable String slug) {
        return brandService.detail(slug);
    }
}

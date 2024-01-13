package com.example.lonua.product.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.lonua.brand.model.entity.Brand;
import com.example.lonua.category.model.entity.Category;
import com.example.lonua.config.BaseRes;
import com.example.lonua.exception.ErrorCode;
import com.example.lonua.exception.exception.CategoryException;
import com.example.lonua.product.model.entity.ProductCount;
import com.example.lonua.product.model.entity.ProductImage;
import com.example.lonua.product.model.entity.ProductIntrodImage;
import com.example.lonua.product.model.request.PostRegisterProductReq;
import com.example.lonua.product.model.entity.Product;
import com.example.lonua.product.model.response.GetListProductRes;
import com.example.lonua.product.model.response.GetReadProductRes;
import com.example.lonua.product.model.response.GetProductIntrodRes;
import com.example.lonua.product.model.response.PostRegisterProductRes;
import com.example.lonua.product.repository.ProductCountRepository;
import com.example.lonua.product.repository.ProductRepository;
import com.example.lonua.style.model.entity.Style;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;
    private final ProductIntrodImageService productIntrodImageService;
    private final ProductCountRepository productCountRepository;


    @Transactional
    public BaseRes register(PostRegisterProductReq postRegisterProductReq, MultipartFile[] productFiles, MultipartFile[] productIntrodFiles) {

        Optional<Product> result = productRepository.findByProductName(postRegisterProductReq.getProductName());
        if(result.isPresent()) {
            throw new CategoryException(ErrorCode.DUPLICATED_PRODUCT, String.format("Product Name is %s", postRegisterProductReq.getProductName()));
        }

        Product product = Product.builder()
                .brand(Brand.builder()
                        .brandIdx(postRegisterProductReq.getBrand_idx())
                        .build())
                .category(Category.builder()
                        .categoryIdx(postRegisterProductReq.getCategory_idx())
                        .build())
                .style(Style.builder()
                        .styleIdx(postRegisterProductReq.getStyle_idx())
                        .build())
                .productName(postRegisterProductReq.getProductName())
                .quantity(postRegisterProductReq.getQuantity())
                .price(postRegisterProductReq.getPrice())
                .shoulderWidth(postRegisterProductReq.getShoulderWidth())
                .chestSize(postRegisterProductReq.getChestSize())
                .armLength(postRegisterProductReq.getArmLength())
                .topLength(postRegisterProductReq.getTopLength())
                .waistline(postRegisterProductReq.getWaistline())
                .hipCircumference(postRegisterProductReq.getHipCircumference())
                .thighCircumference(postRegisterProductReq.getThighCircumference())
                .crotchLength(postRegisterProductReq.getCrotchLength())
                .hemLength(postRegisterProductReq.getHemLength())
                .totalBottomLength(postRegisterProductReq.getTotalBottomLength())
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .status(false)
                .build();

        product = productRepository.save(product);

        productCountRepository.save(ProductCount.builder()
                .product(product)
                .likeCount(0)
                .upperType1Count(0)
                .upperType2Count(0)
                .upperType3Count(0)
                .lowerType1Count(0)
                .lowerType2Count(0)
                .lowerType3Count(0)
                .build());

        List<String> productImageList = productImageService.registerProductImage(product, productFiles);
        List<String> productIntrodImageList = productIntrodImageService.registerProductIntrodImage(product, productIntrodFiles);

        PostRegisterProductRes postRegisterProductRes = PostRegisterProductRes.builder()
                .productIdx(product.getProductIdx())
                .productName(product.getProductName())
                .quantity(product.getQuantity())
                .price(product.getPrice())
                .shoulderWidth(product.getShoulderWidth())
                .chestSize(product.getChestSize())
                .armLength(product.getArmLength())
                .topLength(product.getTopLength())
                .waistline(product.getWaistline())
                .hipCircumference(product.getHipCircumference())
                .thighCircumference(product.getThighCircumference())
                .crotchLength(product.getCrotchLength())
                .hemLength(product.getHemLength())
                .totalBottomLength(product.getTotalBottomLength())
                .productImage(productImageList)
                .productIntroductionImage(productIntrodImageList)
                .build();

        BaseRes baseRes = BaseRes.builder()
                .code(200)
                .isSuccess(true)
                .message("상품 등록 성공")
                .result(postRegisterProductRes)
                .build();

        return baseRes;
    }

    // 상품 리스트 출력(페이지 별)
    public BaseRes list(Integer page, Integer size) {

        // 페이징 기능 사용(QueryDSL)
        Pageable pageable = PageRequest.of(page-1, size);
        Page<Product> productList = productRepository.findList(pageable);

        List<GetListProductRes> getListProductResList = new ArrayList<>();

        for(Product product : productList) {

            List<ProductImage> productImageList = product.getProductImageList();
            ProductImage productImage = productImageList.get(0);
            String image = productImage.getProductImage();
            // 상품의 이미지중 첫번 째 이미지만 뽑아옴

            GetListProductRes getListProductRes = GetListProductRes.builder()
                        .brandName(product.getBrand().getBrandName())
                        .productIdx(product.getProductIdx())
                        .productName(product.getProductName())
                        .productImage(image)
                        .price(product.getPrice())
                        .likeCount(product.getProductCount().getLikeCount())
                        .build();

            getListProductResList.add(getListProductRes);
        }
        BaseRes baseRes = BaseRes.builder()
                .code(200)
                .isSuccess(true)
                .message("요청 성공")
                .result(getListProductResList)
                .build();

        return baseRes;
    }
}


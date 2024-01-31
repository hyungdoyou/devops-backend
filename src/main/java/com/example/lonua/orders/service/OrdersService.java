package com.example.lonua.orders.service;


import com.example.lonua.common.BaseRes;
import com.example.lonua.orders.exception.OrdersNotFoundException;
import com.example.lonua.orders.model.entity.Orders;
import com.example.lonua.orders.model.entity.OrdersProduct;
import com.example.lonua.orders.model.request.PatchUpdateOrdersReq;
import com.example.lonua.orders.model.request.PostCreateOrdersReq;
import com.example.lonua.orders.model.response.GetCreateOrdersRes;
import com.example.lonua.orders.model.response.GetListOrdersRes;
import com.example.lonua.orders.model.response.GetReadOrdersRes;
import com.example.lonua.orders.model.response.PostCreateOrdersRes;
import com.example.lonua.orders.repository.OrdersProductRepository;
import com.example.lonua.orders.repository.OrdersRepository;
import com.example.lonua.product.exception.ProductNotFoundException;
import com.example.lonua.product.model.entity.Product;
import com.example.lonua.product.model.entity.ProductCount;
import com.example.lonua.product.repository.ProductCountRepository;
import com.example.lonua.product.repository.ProductRepository;
import com.example.lonua.user.model.entity.User;
import com.example.lonua.user.repository.UserRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrdersService {

    private final IamportClient iamportClient;
    private final ProductRepository productRepository;
    private final OrdersRepository ordersRepository;
    private final OrdersProductRepository ordersProductRepository;
    private final ProductCountRepository productCountRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = false)
    public BaseRes createOrder(User user, PostCreateOrdersReq postCreateOrdersReq) {
        Orders orders = Orders.builder()
                .user(user)
                .impUid(postCreateOrdersReq.getImpUid())
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .status("주문접수")
                .build();

        orders = ordersRepository.save(orders);

        // 마일리지 적립
        user.setUserMileage(postCreateOrdersReq.getMileage());
        userRepository.save(user);

        List<GetCreateOrdersRes> getCreateOrdersResList = new ArrayList<>();

        for (Integer idx : postCreateOrdersReq.getProductIdxList()) {
            Optional<Product> productIdx = productRepository.findByProductIdx(idx);

            if(productIdx.isPresent()) {
                // 주문 상품 DB에 추가
                OrdersProduct ordersProduct = OrdersProduct.builder()
                        .orders(orders)
                        .product(Product.builder().productIdx(idx).build())
                        .build();

                ordersProductRepository.save(ordersProduct);

                // 회원 상, 하체 유형에 따라 상품의 카운트 수 증가
                Optional<ProductCount> result = productCountRepository.findByProduct_ProductIdx(idx);
                ProductCount productCount = result.get();
                productCount.increaseUpperCount(user.getUpperType());
                productCount.increaseLowerCount(user.getLowerType());

                Optional<Product> product = productRepository.findByProductIdx(idx);
                Product userProduct = product.get();

                GetCreateOrdersRes getCreateOrdersRes = GetCreateOrdersRes.builder()
                        .brandName(userProduct.getBrand().getBrandName())
                        .productName(userProduct.getProductName())
                        .salePrice(userProduct.getSalePrice())
                        .build();

                getCreateOrdersResList.add(getCreateOrdersRes);
            } else {
                throw ProductNotFoundException.forIdx(idx);
            }
        }

        PostCreateOrdersRes postCreateOrdersRes = PostCreateOrdersRes.builder()
                .userName(user.getName())
                .userAddr(user.getUserAddr())
                .userPhoneNumber(user.getUserPhoneNumber())
                .totalAmount(postCreateOrdersReq.getAmount())
                .impUid(postCreateOrdersReq.getImpUid())
                .payMethod(postCreateOrdersReq.getPayMethod())
                .productList(getCreateOrdersResList)
                .build();

        return BaseRes.builder()
                .code(200)
                .isSuccess(true)
                .message("상품 주문 성공")
                .result(postCreateOrdersRes)
                .build();
    }

    // 주문 내역 조회
    @Transactional(readOnly = true)
    public BaseRes list(User user, Integer page, Integer size) {

        Pageable pageable = PageRequest.of(page-1, size);
        Page<Orders> ordersList = ordersRepository.findList(pageable, user.getUserIdx());

        List<GetListOrdersRes> getListOrdersResList = new ArrayList<>();

        if(ordersList != null) {
            for(Orders orders : ordersList) {
                for(OrdersProduct ordersProduct : orders.getOrdersProductList()) {
                    GetListOrdersRes getListOrdersRes = GetListOrdersRes.builder()
                            .brandName(ordersProduct.getProduct().getBrand().getBrandName())
                            .productName(ordersProduct.getProduct().getProductName())
                            .productImage(ordersProduct.getProduct().getProductImageList().get(0).getProductImage())
                            .price(ordersProduct.getProduct().getPrice())
                            .salePrice(ordersProduct.getProduct().getSalePrice())
                            .createdAt(ordersProduct.getOrders().getCreatedAt())
                            .build();

                    getListOrdersResList.add(getListOrdersRes);
                }
            }
            return BaseRes.builder()
                    .code(200)
                    .isSuccess(true)
                    .message("요청 성공")
                    .result(getListOrdersResList)
                    .build();
        } else {
            throw new OrdersNotFoundException(null);
        }
    }

    // 주문 상세 내역 조회
    @Transactional(readOnly = true)
    public BaseRes read(User user, Integer ordersIdx, Integer productIdx) {
        Optional<OrdersProduct> result = ordersProductRepository.findOrdersProduct(user.getUserIdx(), ordersIdx, productIdx);

        if (result.isPresent()) {
            OrdersProduct ordersProduct = result.get();

            GetReadOrdersRes getReadOrdersRes = GetReadOrdersRes.builder()
                    .brandName(ordersProduct.getProduct().getProductName())
                    .productName(ordersProduct.getProduct().getProductName())
                    .productImage(ordersProduct.getProduct().getProductImageList().get(0).getProductImage())
                    .price(ordersProduct.getProduct().getPrice())
                    .salePrice(ordersProduct.getProduct().getSalePrice())
                    .userName(ordersProduct.getOrders().getUser().getName())
                    .userPhoneNumber(ordersProduct.getOrders().getUser().getUserPhoneNumber())
                    .userAddr(ordersProduct.getOrders().getUser().getUserAddr())
                    .deliveryMassage("배송 잘 부탁드립니다.")
                    .build();
            return BaseRes.builder()
                    .code(200)
                    .isSuccess(true)
                    .message("요청 성공")
                    .result(getReadOrdersRes)
                    .build();
            } else {
            throw new OrdersNotFoundException(ordersIdx);
        }
    }

    @Transactional(readOnly = false)
    public BaseRes updateStatus(PatchUpdateOrdersReq patchUpdateOrdersReq) {
        Optional<Orders> result = ordersRepository.findByOrdersIdx(patchUpdateOrdersReq.getOrdersIdx());
        if(result.isPresent()) {
            Orders orders = result.get();
            orders.updateStatus(patchUpdateOrdersReq);
            orders.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
            ordersRepository.save(orders);

            return BaseRes.builder()
                    .code(200)
                    .isSuccess(true)
                    .message("주문 상태 수정 성공")
                    .result(patchUpdateOrdersReq.getStatus())
                    .build();
        } else {
            throw new OrdersNotFoundException(patchUpdateOrdersReq.getOrdersIdx());
        }
    }
    @Transactional(readOnly = false)
    public BaseRes cancle(Integer idx) {
        Optional<Orders> ordersStauts = ordersRepository.findByOrdersIdx(idx);
        if(ordersStauts.isPresent()) {
            Orders orders = ordersStauts.get();
            if(orders.getStatus().equals("주문접수") || orders.getStatus().equals("결제완료")|| orders.getStatus().equals("상품준비중")) {
                Integer result1 = ordersProductRepository.deleteByOrders_OrdersIdx(idx);
                Integer result2 = ordersRepository.deleteByOrdersIdx(idx);

                if(!result1.equals(0) && !result2.equals(0)) {
                    return BaseRes.builder()
                        .code(200)
                        .isSuccess(true)
                        .message("요청 성공")
                        .result("상품이 삭제되었습니다.")
                        .build();
                } else {
                    throw new OrdersNotFoundException(idx);
                }
            } else {
                return BaseRes.builder()
                        .code(400)
                        .isSuccess(false)
                        .message("요청 실패")
                        .result("상품의 배송이 시작되어 주문을 취소할 수 없습니다.")
                        .build();
            }
        } else {
            throw new OrdersNotFoundException(idx);
        }
    }


    //----------------------------------카카오페이 결제를 통한 주문------------------------------------------------
//    @Transactional
//    public void createOrder(User user, PostCreateOrdersReq postCreateOrdersReq) {
//        Orders orders = Orders.builder()
//                .user(user)
//                .impUid(postCreateOrdersReq.getImpUid())
//                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
//                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
//                .status("주문접수")
//                .build();
//
//        orders = ordersRepository.save(orders);
//
//        for (Integer idx : postCreateOrdersReq.getProductIdxList()) {
//            // 주문 상품 DB에 추가
//            ordersProductRepository.save(OrdersProduct.builder()
//                    .orders(orders)
//                    .product(Product.builder().productIdx(idx).build())
//                    .build());
//
//            // 회원 상, 하체 유형에 따라 상품의 카운트 수 증가
//            Optional<ProductCount> result = productCountRepository.findByProduct_ProductIdx(idx);
//            ProductCount productCount = result.get();
//            productCount.increaseUpperCount(user.getUpperType());
//            productCount.increaseLowerCount(user.getLowerType());
//        }
//    }

    public BaseRes paymentValidation(User user, PostCreateOrdersReq postCreateOrdersReq) throws IamportResponseException, IOException {

        Integer totalPrice = 0;
        for(Integer idx : postCreateOrdersReq.getProductIdxList()) {
            Optional<Product> result = productRepository.findByProductIdx(idx);
            if(result.isPresent()) {
                Product product = result.get();
                totalPrice += product.getSalePrice();
            }
        }

        if(postCreateOrdersReq.getAmount().equals(totalPrice) ) {
            createOrder(user, postCreateOrdersReq);  // 주문 생성

            List<GetCreateOrdersRes> getListOrdersResList = new ArrayList<>();
            for(Integer idx : postCreateOrdersReq.getProductIdxList()) {
                Optional<Product> result = productRepository.findByProductIdx(idx);

                Product product = result.get();
                GetCreateOrdersRes getListOrdersRes = GetCreateOrdersRes.builder()
                        .brandName(product.getBrand().getBrandName())
                        .productName(product.getProductName())
                        .salePrice(product.getSalePrice())
                        .build();

                getListOrdersResList.add(getListOrdersRes);
            }

            PostCreateOrdersRes postCreateOrdersRes = PostCreateOrdersRes.builder()
                    .userName(user.getName())
                    .userAddr(user.getUserAddr())
                    .userPhoneNumber(user.getUserPhoneNumber())
                    .totalAmount(postCreateOrdersReq.getAmount())
                    .impUid(postCreateOrdersReq.getImpUid())
                    .payMethod(postCreateOrdersReq.getPayMethod())
                    .productList(getListOrdersResList)
                    .build();

            BaseRes baseRes = BaseRes.builder()
                    .code(200)
                    .isSuccess(true)
                    .message("상품 주문 성공")
                    .result(postCreateOrdersRes)
                    .build();

            return baseRes;
        } else{
            cancelPayment(postCreateOrdersReq.getImpUid());  // 취소 기능
            return null;
        }
    }

    public IamportResponse getPaymentInfo(String impUid) throws IamportResponseException, IOException {
        IamportResponse<Payment> response = iamportClient.paymentByImpUid(impUid);

        return response;
    }

    public void cancelPayment(String impUid) throws IamportResponseException, IOException {
        CancelData cancelData = new CancelData(impUid,true);
        iamportClient.cancelPaymentByImpUid(cancelData);
    }
    //--------------------------------------------여기까지---------------------------------------------------------
}

package org.masterh.orderservice.dto;

/**
 * @ClassName OrderResponse
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:57
 * @Version 1.0
 **/
public class OrderResponse {
    private Integer id;
    private String productName;
    private Integer amount;
    private UserResponse user;

    public OrderResponse(
            Integer id,
            String productName,
            Integer amount,
            UserResponse user
    ) {
        this.id = id;
        this.productName = productName;
        this.amount = amount;
        this.user = user;
    }

    public Integer getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getAmount() {
        return amount;
    }

    public UserResponse getUser() {
        return user;
    }
}

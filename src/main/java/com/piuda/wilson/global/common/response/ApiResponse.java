package com.piuda.wilson.global.common.response;

public record ApiResponse<T>(String message, T data) {

    private static final String SUCCESS_MESSAGE = "SUCCESS";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_MESSAGE, null);
    }
}

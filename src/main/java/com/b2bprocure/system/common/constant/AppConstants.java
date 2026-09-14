package com.b2bprocure.system.common.constant;

public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    // Pagination defaults
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "desc";
    public static final int MAX_PAGE_SIZE = 100;

    // Date and time formats
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_ZONE = "Asia/Ho_Chi_Minh";

    // Currency
    public static final String DEFAULT_CURRENCY = "VND";

    // Commission defaults
    public static final double DEFAULT_COMMISSION_RATE = 5.0;
}

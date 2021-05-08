package com.beyond.generator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author chenshipeng
 * @date 2021/05/07
 */
public class TypeConverter {
    public static Class<?> toJavaType(String jdbcType) {
        switch (jdbcType) {
            case "int":
            case "tinyint":
            case "smallint":
            case "mediumint":
                return Integer.class;
            case "bigint":
                return Long.class;
            case "float":
                return Float.class;
            case "double":
                return Double.class;
            case "varchar":
            case "char":
            case "text":
            case "longtext":
                return String.class;
            case "decimal":
                return BigDecimal.class;
            case "date":
            case "datetime":
            case "timestamp":
                return LocalDateTime.class;
            case "blob":
            case "longblob":
                return byte[].class;

        }
        throw new RuntimeException("type convert fail:" + jdbcType);
    }


    public static String toCommonJdbcType(String jdbcType) {
        switch (jdbcType) {
            case "int":
                return "integer";
            case "datetime":
                return "date";
            case "text":
                return "longvarchar";
            default:
                return jdbcType;
        }
    }
}

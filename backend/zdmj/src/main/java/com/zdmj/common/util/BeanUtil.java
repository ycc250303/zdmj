package com.zdmj.common.util;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean工具类
 * 提供Bean属性复制的便捷方法
 */
public class BeanUtil {

    /**
     * 复制源对象中非空的属性到目标对象
     * 只复制源对象中不为null的属性，忽略null值
     * 
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyNonNullProperties(Object source, Object target) {
        // 获取源对象中所有非null的属性名
        String[] nullPropertyNames = getNullPropertyNames(source);
        // 复制非null属性
        BeanUtils.copyProperties(source, target, nullPropertyNames);
    }

    /**
     * 复制源对象中非空的属性到目标对象，并排除指定字段
     * 只复制源对象中不为null的属性，忽略null值和指定的排除字段
     * 
     * @param source           源对象
     * @param target           目标对象
     * @param ignoreProperties 需要排除的字段名数组
     */
    public static void copyNonNullProperties(Object source, Object target, String... ignoreProperties) {
        // 获取源对象中所有非null的属性名
        String[] nullPropertyNames = getNullPropertyNames(source);

        // 合并 null 属性名和需要排除的属性名
        Set<String> ignoreSet = new HashSet<>();
        if (nullPropertyNames != null) {
            ignoreSet.addAll(Arrays.asList(nullPropertyNames));
        }
        if (ignoreProperties != null && ignoreProperties.length > 0) {
            ignoreSet.addAll(Arrays.asList(ignoreProperties));
        }

        // 复制非null且不在排除列表中的属性
        String[] allIgnoreProperties = ignoreSet.toArray(new String[0]);
        BeanUtils.copyProperties(source, target, allIgnoreProperties);
    }

    /**
     * 获取对象中所有null属性的名称数组
     * 
     * @param source 源对象
     * @return null属性名数组
     */
    private static String[] getNullPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for (PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }
}

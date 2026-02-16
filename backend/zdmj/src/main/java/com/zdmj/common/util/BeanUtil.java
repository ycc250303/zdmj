package com.zdmj.common.util;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
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

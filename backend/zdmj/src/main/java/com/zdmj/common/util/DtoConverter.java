package com.zdmj.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO 转换工具类
 * 提供 Entity 和 DTO 之间的通用转换方法
 * 
 * 规律总结：
 * 1. Entity 继承 BaseEntity（包含 createdAt, updatedAt）
 * 2. Entity 有 userId 字段，DTO 通常没有
 * 3. 大部分字段名称相同，可以直接复制
 * 4. 需要排除的字段：userId, createdAt, updatedAt
 */
@Slf4j
public class DtoConverter {

    /**
     * 默认排除的字段（Entity 中有但 DTO 中通常没有的字段）
     */
    private static final String[] DEFAULT_IGNORE_PROPERTIES = {
            "userId", // DTO 中通常不需要 userId（从上下文获取）
            "createdAt", // BaseEntity 的字段
            "updatedAt" // BaseEntity 的字段
    };

    /**
     * 将 Entity 转换为 DTO
     * 自动排除 userId, createdAt, updatedAt 字段
     * 
     * @param entity   实体对象
     * @param dtoClass DTO 类
     * @param <E>      Entity 类型
     * @param <D>      DTO 类型
     * @return DTO 对象，如果 entity 为 null 则返回 null
     */
    public static <E, D> D toDTO(E entity, Class<D> dtoClass) {
        if (entity == null) {
            return null;
        }

        try {
            D dto = dtoClass.getDeclaredConstructor().newInstance();
            // 使用 BeanUtil 复制非空属性，并排除默认字段
            BeanUtil.copyNonNullProperties(entity, dto, DEFAULT_IGNORE_PROPERTIES);
            return dto;
        } catch (Exception e) {
            log.error("Entity 转 DTO 失败: {}", e.getMessage(), e);
            throw new RuntimeException("DTO 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 Entity 转换为 DTO（自定义排除字段）
     * 
     * @param entity           实体对象
     * @param dtoClass         DTO 类
     * @param ignoreProperties 需要排除的字段名数组
     * @param <E>              Entity 类型
     * @param <D>              DTO 类型
     * @return DTO 对象，如果 entity 为 null 则返回 null
     */
    public static <E, D> D toDTO(E entity, Class<D> dtoClass, String... ignoreProperties) {
        if (entity == null) {
            return null;
        }

        try {
            D dto = dtoClass.getDeclaredConstructor().newInstance();
            // 合并默认排除字段和自定义排除字段
            String[] allIgnoreProperties = mergeIgnoreProperties(ignoreProperties);
            BeanUtil.copyNonNullProperties(entity, dto, allIgnoreProperties);
            return dto;
        } catch (Exception e) {
            log.error("Entity 转 DTO 失败: {}", e.getMessage(), e);
            throw new RuntimeException("DTO 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 DTO 转换为 Entity
     * 自动排除 createdAt, updatedAt 字段（这些字段由数据库自动管理）
     * 
     * @param dto         DTO 对象
     * @param entityClass Entity 类
     * @param <D>         DTO 类型
     * @param <E>         Entity 类型
     * @return Entity 对象，如果 dto 为 null 则返回 null
     */
    public static <D, E> E toEntity(D dto, Class<E> entityClass) {
        if (dto == null) {
            return null;
        }

        try {
            E entity = entityClass.getDeclaredConstructor().newInstance();
            // 排除 BaseEntity 的字段（这些字段由数据库自动管理）
            BeanUtil.copyNonNullProperties(dto, entity, "createdAt", "updatedAt");
            return entity;
        } catch (Exception e) {
            log.error("DTO 转 Entity 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Entity 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 DTO 转换为 Entity（自定义排除字段）
     * 
     * @param dto              DTO 对象
     * @param entityClass      Entity 类
     * @param ignoreProperties 需要排除的字段名数组
     * @param <D>              DTO 类型
     * @param <E>              Entity 类型
     * @return Entity 对象，如果 dto 为 null 则返回 null
     */
    public static <D, E> E toEntity(D dto, Class<E> entityClass, String... ignoreProperties) {
        if (dto == null) {
            return null;
        }

        try {
            E entity = entityClass.getDeclaredConstructor().newInstance();
            // 合并默认排除字段和自定义排除字段
            String[] allIgnoreProperties = mergeIgnoreProperties("createdAt", "updatedAt", ignoreProperties);
            BeanUtil.copyNonNullProperties(dto, entity, allIgnoreProperties);
            return entity;
        } catch (Exception e) {
            log.error("DTO 转 Entity 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Entity 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量将 Entity 列表转换为 DTO 列表
     * 
     * @param entities Entity 列表
     * @param dtoClass DTO 类
     * @param <E>      Entity 类型
     * @param <D>      DTO 类型
     * @return DTO 列表
     */
    public static <E, D> List<D> toDTOList(List<E> entities, Class<D> dtoClass) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(entity -> toDTO(entity, dtoClass))
                .collect(Collectors.toList());
    }

    /**
     * 批量将 DTO 列表转换为 Entity 列表
     * 
     * @param dtos        DTO 列表
     * @param entityClass Entity 类
     * @param <D>         DTO 类型
     * @param <E>         Entity 类型
     * @return Entity 列表
     */
    public static <D, E> List<E> toEntityList(List<D> dtos, Class<E> entityClass) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .map(dto -> toEntity(dto, entityClass))
                .collect(Collectors.toList());
    }

    /**
     * 合并排除字段数组
     */
    private static String[] mergeIgnoreProperties(String... customIgnoreProperties) {
        if (customIgnoreProperties == null || customIgnoreProperties.length == 0) {
            return DEFAULT_IGNORE_PROPERTIES;
        }

        // 合并数组并去重
        Set<String> ignoreSet = new HashSet<>(Arrays.asList(DEFAULT_IGNORE_PROPERTIES));
        ignoreSet.addAll(Arrays.asList(customIgnoreProperties));
        return ignoreSet.toArray(new String[0]);
    }

    /**
     * 合并排除字段数组（带默认字段）
     */
    private static String[] mergeIgnoreProperties(String defaultField1, String defaultField2,
            String... customIgnoreProperties) {
        Set<String> ignoreSet = new HashSet<>();
        ignoreSet.add(defaultField1);
        ignoreSet.add(defaultField2);

        if (customIgnoreProperties != null && customIgnoreProperties.length > 0) {
            ignoreSet.addAll(Arrays.asList(customIgnoreProperties));
        }

        return ignoreSet.toArray(new String[0]);
    }
}

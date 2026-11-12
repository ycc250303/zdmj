package com.zdmj.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 从认证主体解析 Controller 的操作者参数，客户端参数不参与身份构造。
 */
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentActor.class;
    }

    /**
     * 取得已认证身份。匿名请求返回未登录，主体类型不匹配时暴露配置缺陷。
     *
     * @param parameter 操作者参数声明
     * @param mavContainer MVC 模型容器
     * @param webRequest 当前请求
     * @param binderFactory 参数绑定工厂
     * @return 认证主体中的同一操作者
     * @throws BusinessException 请求未登录
     */
    @Override
    public CurrentActor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (!(authentication.getPrincipal() instanceof CurrentActor actor)) {
            throw new IllegalStateException("认证主体必须为 CurrentActor");
        }
        return actor;
    }
}

package com.hairmony.warehouse.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class CurrentUriInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView != null && !modelAndView.getViewName().startsWith("redirect:")) {
            String uri = request.getRequestURI();
            modelAndView.addObject("currentUri", uri);
            modelAndView.addObject("activeModule",
                    uri.startsWith("/clientcare") ? "clientcare" : "warehouse");
        }
    }
}

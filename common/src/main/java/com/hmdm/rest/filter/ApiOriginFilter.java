/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.filter;

import com.google.inject.Singleton;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>$</p>
 *
 * @author isv
 */
@Singleton
public class ApiOriginFilter implements Filter {
    private final Set<String> allowedOrigins;

    public ApiOriginFilter() {
        this("");
    }

    @Inject
    public ApiOriginFilter(@Named("cors.allowed.origins") String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            this.allowedOrigins = Collections.emptySet();
        } else {
            this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .collect(Collectors.toCollection(HashSet::new));
        }
    }

    /**
     * doFilter
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String origin = req.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Vary", "Origin");
            res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
            res.setHeader("Access-Control-Allow-Headers", "Content-Type, api_key, Authorization");
        }
        chain.doFilter(request, response);
    }

    boolean isAllowedOrigin(String origin) {
        return this.allowedOrigins.contains(origin);
    }

    /**
     * destroy
     */
    @Override
    public void destroy() {
    }

    /**
     * init
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
}

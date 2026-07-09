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

package com.hmdm.auth;

import com.google.inject.Inject;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;
import java.util.UUID;

/**
 * <p>Authenticates users against an LDAP directory (activated via {@code auth.class=Ldap}).</p>
 *
 * <p>{@link #authenticate(User, String)} performs an LDAP simple bind using the supplied
 * (plaintext) password. Two modes are supported, selected by {@code ldap.admin.bind}:</p>
 * <ul>
 *   <li><b>direct</b> — bind directly as a DN built from {@code ldap.user.dn} with {@code {0}} = login;</li>
 *   <li><b>admin</b> — bind as {@code ldap.admin.dn}, search {@code ldap.base.dn} for
 *       {@code (ldap.username.attribute=login)}, then bind as the found DN.</li>
 * </ul>
 *
 * <p>On a successful first login, a local user is auto-provisioned (role {@code ldap.default.role},
 * customer {@code ldap.customer.id}) so the panel's permissions/relations keep working.</p>
 */
@Singleton
public class LdapAuth implements HmdmAuthInterface {

    private static final Logger log = LoggerFactory.getLogger(LdapAuth.class);

    private final UnsecureDAO userDAO;
    private final UserMapper userMapper;

    private final String host;
    private final int port;
    private final String baseDn;
    private final String adminDn;
    private final String adminPassword;
    private final String usernameAttribute;
    private final String userDnTemplate;
    private final boolean adminBind;
    private final int defaultRoleId;
    private final int customerId;

    @Inject
    public LdapAuth(UnsecureDAO userDAO,
                    UserMapper userMapper,
                    @Named("ldap.host") String host,
                    @Named("ldap.port") int port,
                    @Named("ldap.base.dn") String baseDn,
                    @Named("ldap.admin.dn") String adminDn,
                    @Named("ldap.admin.password") String adminPassword,
                    @Named("ldap.username.attribute") String usernameAttribute,
                    @Named("ldap.user.dn") String userDnTemplate,
                    @Named("ldap.admin.bind") boolean adminBind,
                    @Named("ldap.default.role") String defaultRole,
                    @Named("ldap.customer.id") Integer customerId) {
        this.userDAO = userDAO;
        this.userMapper = userMapper;
        this.host = host;
        this.port = port;
        this.baseDn = baseDn;
        this.adminDn = adminDn;
        this.adminPassword = adminPassword;
        this.usernameAttribute = usernameAttribute == null || usernameAttribute.isEmpty() ? "uid" : usernameAttribute;
        this.userDnTemplate = userDnTemplate;
        this.adminBind = adminBind;
        this.defaultRoleId = parseIntOr(defaultRole, 2);   // 2 = Admin by default
        this.customerId = customerId != null ? customerId : 1;
    }

    private static int parseIntOr(String s, int def) {
        try {
            return s == null || s.trim().isEmpty() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public User findUser(String login) {
        if (login == null || login.trim().isEmpty()) {
            return null;
        }
        User existing = userDAO.findByLoginOrEmail(login);
        if (existing != null) {
            return existing;
        }
        // Unknown locally: return a transient user so authenticate() is invoked. It will be
        // persisted only if the LDAP bind succeeds. Session fields are pre-filled from config.
        User transientUser = new User();
        transientUser.setId(0);
        transientUser.setLogin(login);
        transientUser.setName(login);
        transientUser.setCustomerId(this.customerId);
        UserRole role = new UserRole();
        role.setId(this.defaultRoleId);
        transientUser.setUserRole(role);
        return transientUser;
    }

    @Override
    public boolean authenticate(User user, String password) {
        // Reject empty passwords: an empty password on a simple bind is treated as an
        // ANONYMOUS bind by most servers and would otherwise "succeed" (auth bypass).
        if (user == null || password == null || password.isEmpty()) {
            return false;
        }
        final String login = user.getLogin();
        if (login == null || login.trim().isEmpty()) {
            return false;
        }

        try {
            String bindDn;
            if (adminBind) {
                bindDn = resolveDnViaAdmin(login);
                if (bindDn == null) {
                    return false; // user not found in directory
                }
            } else {
                // Direct bind: substitute the RDN-escaped login into the DN template.
                bindDn = userDnTemplate.replace("{0}", escapeDnValue(login));
            }

            if (!bind(bindDn, password)) {
                return false;
            }

            // Bind succeeded. Provision the local user on first login.
            if (user.getId() == null || user.getId() == 0) {
                provisionLocalUser(user);
            }
            return true;
        } catch (Exception e) {
            // Fail closed on any LDAP/network error.
            log.warn("LDAP authentication failed for '{}': {}", login, e.getMessage());
            return false;
        }
    }

    /** Binds as the admin, searches the directory for the login, and returns the user's DN (or null). */
    private String resolveDnViaAdmin(String login) throws Exception {
        Hashtable<String, String> env = ldapEnv(adminDn, adminPassword);
        InitialDirContext ctx = new InitialDirContext(env);
        try {
            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(new String[]{});
            String filter = "(" + usernameAttribute + "=" + escapeFilterValue(login) + ")";
            javax.naming.NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, sc);
            if (results.hasMore()) {
                SearchResult r = results.next();
                String dn = r.getNameInNamespace();
                return dn;
            }
            return null;
        } finally {
            try { ctx.close(); } catch (Exception ignore) {}
        }
    }

    private boolean bind(String dn, String password) {
        Hashtable<String, String> env = ldapEnv(dn, password);
        InitialDirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (Exception ignore) {}
            }
        }
    }

    private Hashtable<String, String> ldapEnv(String principal, String credentials) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, credentials);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        return env;
    }

    private void provisionLocalUser(User user) {
        try {
            user.setPassword(UUID.randomUUID().toString()); // random local password; login is via LDAP only
            user.setPasswordReset(false);
            user.setAllDevicesAvailable(true);
            user.setAllConfigAvailable(true);
            user.setAuthToken(UUID.randomUUID().toString().replace("-", ""));
            user.setPasswordResetToken(null);
            if (user.getCustomerId() == 0) {
                user.setCustomerId(this.customerId);
            }
            userMapper.insert(user);
            log.info("Provisioned local user '{}' from LDAP (customer {}, role {})",
                    user.getLogin(), user.getCustomerId(), this.defaultRoleId);
        } catch (Exception e) {
            // If provisioning fails, treat authentication as failed to avoid a half-created session.
            log.error("Failed to provision local user '{}' from LDAP: {}", user.getLogin(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ---- LDAP escaping (defense against injection) -------------------------------------------

    /** Escapes a value used as an RDN value in a DN (RFC 4514). */
    static String escapeDnValue(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': case ',': case '+': case '"': case '<': case '>': case ';': case '=':
                    sb.append('\\').append(c);
                    break;
                case '#':
                    if (i == 0) sb.append('\\');
                    sb.append(c);
                    break;
                case ' ':
                    if (i == 0 || i == value.length() - 1) sb.append('\\');
                    sb.append(c);
                    break;
                case '\0':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Escapes a value used inside a search filter (RFC 4515). */
    static String escapeFilterValue(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\5c"); break;
                case '*':  sb.append("\\2a"); break;
                case '(':  sb.append("\\28"); break;
                case ')':  sb.append("\\29"); break;
                case '\0': sb.append("\\00"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}

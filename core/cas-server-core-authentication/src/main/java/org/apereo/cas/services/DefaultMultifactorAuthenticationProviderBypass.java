package org.apereo.cas.services;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.AuthenticationManager;
import org.apereo.cas.authentication.DefaultAuthenticationBuilder;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProviderBypassProperties;
import org.apereo.cas.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is {@link DefaultMultifactorAuthenticationProviderBypass}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class DefaultMultifactorAuthenticationProviderBypass implements MultifactorAuthenticationProviderBypass {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMultifactorAuthenticationProviderBypass.class);
    private static final long serialVersionUID = 3720922341350004543L;

    /**
     * Bypass settings for this provider.
     */
    protected final MultifactorAuthenticationProviderBypassProperties bypassProperties;

    public DefaultMultifactorAuthenticationProviderBypass(final MultifactorAuthenticationProviderBypassProperties bypassProperties) {
        this.bypassProperties = bypassProperties;
    }

    @Override
    public boolean shouldMultifactorAuthenticationProviderExecute(final Authentication authentication,
                                                                  final RegisteredService registeredService,
                                                                  final MultifactorAuthenticationProvider provider) {
        
        final Principal principal = authentication.getPrincipal();
        LOGGER.debug("Evaluating multifactor authentication bypass properties for principal [{}], service [{}] and provider [{}]",
                principal.getId(), registeredService, provider);
        final boolean bypassByPrincipal = locateMatchingAttributeBasedOnPrincipalAttributes(bypassProperties, principal);
        if (bypassByPrincipal) {
            LOGGER.debug("Bypass rules for principal [{}] indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByAuthn = locateMatchingAttributeBasedOnAuthenticationAttributes(bypassProperties, authentication);
        if (bypassByAuthn) {
            LOGGER.debug("Bypass rules for authentication [{}] indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByAuthnMethod = locateMatchingAttributeValue(
                AuthenticationManager.AUTHENTICATION_METHOD_ATTRIBUTE,
                bypassProperties.getAuthenticationMethodName(),
                authentication.getAttributes(), false
        );
        if (bypassByAuthnMethod) {
            LOGGER.debug("Bypass rules for authentication method [{}] indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByHandlerName = locateMatchingAttributeValue(
                AuthenticationHandler.SUCCESSFUL_AUTHENTICATION_HANDLERS,
                bypassProperties.getAuthenticationHandlerName(),
                authentication.getAttributes(), false
        );
        if (bypassByHandlerName) {
            LOGGER.debug("Bypass rules for authentication handlers [{}] indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByCredType = locateMatchingCredentialType(authentication, bypassProperties.getCredentialClassType());
        if (bypassByCredType) {
            LOGGER.debug("Bypass rules for credential types [{}] indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByService = locateMatchingRegisteredServiceForBypass(authentication, registeredService);
        if (bypassByService) {
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        updateAuthenticationToForgetBypass(authentication, provider, principal);

        return true;
    }

    private static void updateAuthenticationToForgetBypass(final Authentication authentication, final MultifactorAuthenticationProvider provider,
                                                           final Principal principal) {
        LOGGER.debug("Bypass rules for service [{}] indicate the request may be ignored", principal.getId());
        final Authentication newAuthn = DefaultAuthenticationBuilder.newInstance(authentication)
                .addAttribute(AUTHENTICATION_ATTRIBUTE_BYPASS_MFA, Boolean.FALSE)
                .build();
        LOGGER.debug("Updated authentication session to remember bypass for [{}] via [{}]", provider.getId(),
                AUTHENTICATION_ATTRIBUTE_BYPASS_MFA);
        authentication.updateAll(newAuthn);
    }

    private static void updateAuthenticationToRememberBypass(final Authentication authentication, final MultifactorAuthenticationProvider provider,
                                                             final Principal principal) {
        LOGGER.debug("Bypass rules for service [{}] indicate the request may NOT be ignored", principal.getId());
        final Authentication newAuthn = DefaultAuthenticationBuilder.newInstance(authentication)
                .addAttribute(AUTHENTICATION_ATTRIBUTE_BYPASS_MFA, Boolean.TRUE)
                .addAttribute(AUTHENTICATION_ATTRIBUTE_BYPASS_MFA_PROVIDER, provider.getId())
                .build();
        LOGGER.debug("Updated authentication session to NOT remember bypass for [{}] via [{}]", provider.getId(),
                AUTHENTICATION_ATTRIBUTE_BYPASS_MFA);
        authentication.updateAll(newAuthn);
    }

    /**
     * Locate matching registered service property boolean.
     *
     * @param authentication    the authentication
     * @param registeredService the registered service
     * @return true/false
     */
    protected boolean locateMatchingRegisteredServiceForBypass(final Authentication authentication,
                                                               final RegisteredService registeredService) {
        if (registeredService != null && registeredService.getMultifactorPolicy() != null) {
            return registeredService.getMultifactorPolicy().isBypassEnabled();
        }
        return false;
    }

    /**
     * Locate matching credential type boolean.
     *
     * @param authentication      the authentication
     * @param credentialClassType the credential class type
     * @return the boolean
     */
    protected boolean locateMatchingCredentialType(final Authentication authentication, final String credentialClassType) {
        return StringUtils.isNotBlank(credentialClassType) && authentication.getCredentials()
                .stream()
                .filter(e -> e.getCredentialClass().getName().matches(credentialClassType))
                .findAny()
                .isPresent();
    }

    /**
     * Skip bypass and support event based on authentication attributes.
     *
     * @param bypass the bypass settings for the provider.
     * @param authn  the authn
     * @return the boolean
     */
    protected boolean locateMatchingAttributeBasedOnAuthenticationAttributes(
            final MultifactorAuthenticationProviderBypassProperties bypass, final Authentication authn) {
        return locateMatchingAttributeValue(bypass.getAuthenticationAttributeName(),
                bypass.getAuthenticationAttributeValue(), authn.getAttributes());
    }

    /**
     * Skip bypass and support event based on principal attributes.
     *
     * @param bypass    the bypass properties
     * @param principal the principal
     * @return the boolean
     */
    protected boolean locateMatchingAttributeBasedOnPrincipalAttributes(
            final MultifactorAuthenticationProviderBypassProperties bypass, final Principal principal) {
        return locateMatchingAttributeValue(bypass.getPrincipalAttributeName(),
                bypass.getPrincipalAttributeValue(), principal.getAttributes());
    }

    /**
     * Locate matching attribute value boolean.
     *
     * @param attrName   the attr name
     * @param attrValue  the attr value
     * @param attributes the attributes
     * @return true/false
     */
    protected boolean locateMatchingAttributeValue(final String attrName, final String attrValue,
                                                   final Map<String, Object> attributes) {
        return locateMatchingAttributeValue(attrName, attrValue, attributes, true);
    }

    /**
     * Evaluate attribute rules for bypass.
     *
     * @param attrName               the attr name
     * @param attrValue              the attr value
     * @param attributes             the attributes
     * @param matchIfNoValueProvided the force match on value
     * @return true a matching attribute name/value is found
     */
    protected boolean locateMatchingAttributeValue(final String attrName, final String attrValue,
                                                   final Map<String, Object> attributes,
                                                   final boolean matchIfNoValueProvided) {
        LOGGER.debug("Locating matching attribute [{}] with value [{}] amongst the attribute collection [{}]", attrName, attrValue, attributes);
        if (StringUtils.isBlank(attrName)) {
            LOGGER.debug("Failed to match since attribute name is undefined");
            return false;
        }

        final Set<Map.Entry<String, Object>> names = attributes.entrySet()
                .stream()
                .filter(e -> {
                            LOGGER.debug("Attempting to match [{}] against [{}]", attrName, e.getKey());
                            return e.getKey().matches(attrName);
                        }
                ).collect(Collectors.toSet());

        LOGGER.debug("Found [{}] attributes relevant for multifactor authentication bypass", names.size());

        if (names.isEmpty()) {
            return false;
        }

        if (StringUtils.isBlank(attrValue)) {
            LOGGER.debug("No attribute value to match is provided; Match result is set to [{}]", matchIfNoValueProvided);
            return matchIfNoValueProvided;
        }

        final Set<Map.Entry<String, Object>> values = names
                .stream()
                .filter(e -> {

                    final Set<Object> valuesCol = CollectionUtils.toCollection(e.getValue());
                    LOGGER.debug("Matching attribute [{}] with values [{}] against [{}]", e.getKey(), valuesCol, attrValue);

                    return valuesCol.stream()
                            .filter(v -> v.toString().matches(attrValue))
                            .findAny()
                            .isPresent();
                }).collect(Collectors.toSet());

        LOGGER.debug("Matching attribute values remaining are [{}]", values);
        return !values.isEmpty();
    }
}

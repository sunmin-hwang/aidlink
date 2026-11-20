package io.aidlink.policy.application;

import java.net.URI;
import java.util.Locale;

/** Publication rules shared by policy detail, search and saved-policy responses. */
public final class PublicPolicyLink {
    private PublicPolicyLink() { }

    public static String usableUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) return "";
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            if ((host.equals("gov.kr") || host.equals("www.gov.kr"))
                    && path.startsWith("/portal/rcvfvrSvc/dtlEx/")) return "";
            // This retired deep link currently returns the generic benefit list, not the policy.
            if (host.equals("plus.gov.kr")
                    && path.startsWith("/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl")) return "";
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}

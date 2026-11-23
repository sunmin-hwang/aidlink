package io.aidlink.policy.link;

import io.aidlink.policy.application.PublicPolicyLink;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static io.aidlink.policy.link.LinkCheckResult.Status.*;

/** A content match is a review candidate, never automatic publication approval. */
@Component
public class PolicyLinkInspector {
    public LinkCheckResult inspect(String url, String policyName) {
        String current = url == null ? "" : url.trim();
        Integer httpStatus = null;
        try {
            for (int redirects = 0; redirects <= 4; redirects++) {
                if (PublicPolicyLink.usableUrl(current).isEmpty() || !safeDestination(URI.create(current))) {
                    return new LinkCheckResult(BLOCKED_URL, current, httpStatus, "게시 제외 경로이거나 허용되지 않은 외부 주소입니다.");
                }
                Connection.Response response = Jsoup.connect(current)
                        .userAgent("AIDLINK-LinkCheck/1.0")
                        .timeout(8000).maxBodySize(1_000_000)
                        .followRedirects(false).ignoreHttpErrors(true).ignoreContentType(true).execute();
                httpStatus = response.statusCode();
                if (httpStatus >= 300 && httpStatus < 400) {
                    String location = response.header("Location");
                    if (location == null || redirects == 4) {
                        return new LinkCheckResult(OTHER_PAGE, current, httpStatus, "이동 주소가 없거나 이동 횟수를 초과했습니다.");
                    }
                    current = URI.create(current).resolve(location).toString();
                    continue;
                }
                String type = response.contentType();
                if (type == null || !(type.contains("text/html") || type.contains("application/xhtml"))) {
                    return new LinkCheckResult(httpStatus >= 400 ? ERROR_PAGE : OTHER_PAGE, current, httpStatus, "확인 가능한 HTML 본문이 아닙니다.");
                }
                return classify(current, httpStatus, response.parse(), policyName);
            }
        } catch (IOException exception) {
            return new LinkCheckResult(CONNECTION_FAILED, current, httpStatus, "연결·인증서·응답 시간 문제로 본문을 확인하지 못했습니다.");
        } catch (IllegalArgumentException exception) {
            return new LinkCheckResult(BLOCKED_URL, current, httpStatus, "주소 형식을 확인해 주세요.");
        }
        throw new IllegalStateException("unreachable");
    }

    static boolean safeDestination(URI uri) throws IOException {
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) return false;
        host = host.toLowerCase(Locale.ROOT);
        // Limit server-side requests to Korean public/education/provider domains.
        if (!(host.endsWith(".go.kr") || host.endsWith(".gov.kr") || host.endsWith(".or.kr")
                || host.endsWith(".ac.kr") || host.endsWith(".re.kr") || host.equals("gov.kr"))) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) return false;
        }
        return true;
    }

    static LinkCheckResult classify(String url, int code, Document document, String name) {
        document.select("script, style, noscript, nav, header, footer").remove();
        String text = document.text();
        String compact = normalize(text);
        if (containsAny(compact, "서비스점검", "시스템점검", "점검중", "일시중단", "서비스이용불가")) {
            return new LinkCheckResult(MAINTENANCE, url, code, "점검 또는 일시 이용 불가 안내를 확인했습니다.");
        }
        if (code >= 400 || containsAny(compact, "페이지를찾을수없", "존재하지않는페이지", "요청하신페이지가없", "오류가발생", "접근이차단", "accessdenied")) {
            return new LinkCheckResult(ERROR_PAGE, url, code, "HTTP 오류 또는 오류 안내 본문을 확인했습니다.");
        }
        Set<String> tokens = Arrays.stream(name.split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 2).map(PolicyLinkInspector::normalize).collect(Collectors.toSet());
        long matches = tokens.stream().filter(compact::contains).count();
        boolean titleMatches = !normalize(name).isBlank() && compact.contains(normalize(name));
        boolean policyBody = containsAny(compact, "지원대상", "신청방법", "신청절차", "지원내용", "신청대상");
        if (code >= 200 && code < 300 && policyBody
                && (titleMatches || (tokens.size() >= 2 && matches >= Math.max(2, Math.ceil(tokens.size() * 0.7))))) {
            return new LinkCheckResult(CONTENT_MATCH, url, code, "정책명과 신청·지원 안내를 찾았습니다. 게시 전 제공기관과 세부 내용을 직접 검수해 주세요.");
        }
        return new LinkCheckResult(OTHER_PAGE, url, code, "정책 본문 일치를 확인하지 못했습니다. 목록·다른 페이지·스크립트 화면은 수동 검수가 필요합니다.");
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean containsAny(String text, String... values) {
        return Arrays.stream(values).anyMatch(text::contains);
    }
}

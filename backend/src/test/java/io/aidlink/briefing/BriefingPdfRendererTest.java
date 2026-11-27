package io.aidlink.briefing;

import io.aidlink.briefing.application.*;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.period.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class BriefingPdfRendererTest {
    @Test void embedsKoreanFontWrapsLongTextAndUrlsAndPaginates() throws Exception {
        var items=new ArrayList<BriefingContent.Item>();
        for(int i=1;i<=20;i++) items.add(new BriefingContent.Item(i,
                "청년 주거안정 지원 "+i+" - 긴 한글 제목과 여러 조건이 포함된 맞춤 정책 브리핑",
                "공식 제공기관","주거",EligibilityStatus.UNKNOWN,PolicyAnalysisStatus.RAW,
                "추가 확인이 필요한 정책입니다. ".repeat(9),List.of("가구 소득과 거주 조건 확인 필요","한글 외 기호 처리도 확인합니다: 😀"),
                LocalDate.of(2026,11,1),LocalDate.of(2026,12,31),
                new PublicApplicationPeriod(ApplicationWindow.Kind.FIXED,"2026년 11월 1일부터 12월 31일까지",LocalTime.of(9,0),LocalTime.of(18,0),"API",null,null,false),
                "https://agency.go.kr/policy/"+"long-path-".repeat(16)+i,"javascript:alert(1)"));
        var content=new BriefingContent(LocalDate.of(2026,11,26),"SEOUL",125,true,
                "저장한 프로필 기준입니다. 최종 자격은 공식 기관에서 확인해 주세요.",items);
        byte[] bytes=new BriefingPdfRenderer().render(new PersonalBriefingService.Report(UUID.randomUUID(),"COMPLETED",Instant.now(),content,null));
        var output=Path.of("build/briefing-pdf"); Files.createDirectories(output);
        Files.write(output.resolve("briefing-layout.pdf"),bytes);
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            String text=new PDFTextStripper().getText(doc);
            assertThat(text).contains("청년 주거안정 지원 1", "청년 주거안정 지원 20", "가구 소득과 거주 조건 확인 필요", "18:00");
            assertThat(text).doesNotContain("javascript:");
            int links=0;
            for(var page:doc.getPages()) {
                links+=page.getAnnotations().size();
                for(var name:page.getResources().getFontNames()) assertThat(page.getResources().getFont(name).isEmbedded()).isTrue();
            }
            assertThat(links).isEqualTo(20);
            var renderer=new PDFRenderer(doc);
            for(int index:new int[]{0,1,doc.getNumberOfPages()-1})
                ImageIO.write(renderer.renderImageWithDPI(index,110),"PNG",output.resolve("page-"+(index+1)+".png").toFile());
        }
    }

    @Test void handlesEmptyReportAndRefusesFailedReport() throws Exception {
        var content=new BriefingContent(LocalDate.of(2026,11,26),null,0,false,"공식 원문 확인",List.of());
        var renderer=new BriefingPdfRenderer();
        try(var doc=Loader.loadPDF(renderer.render(new PersonalBriefingService.Report(UUID.randomUUID(),"COMPLETED",Instant.now(),content,null)))) {
            assertThat(new PDFTextStripper().getText(doc)).contains("이번 브리핑에 담을 정책이 없습니다.");
        }
        assertThatThrownBy(()->renderer.render(new PersonalBriefingService.Report(UUID.randomUUID(),"FAILED",Instant.now(),null,"실패")))
                .isInstanceOf(IllegalStateException.class);
    }
}

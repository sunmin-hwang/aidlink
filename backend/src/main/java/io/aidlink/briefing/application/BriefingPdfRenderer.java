package io.aidlink.briefing.application;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.*;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.*;
import java.util.*;

@Component
public class BriefingPdfRenderer {
    public byte[] render(PersonalBriefingService.Report report) {
        if (report.content() == null) throw new IllegalStateException("완료된 브리핑만 PDF로 받을 수 있습니다.");
        try (var doc = new PDDocument();
             var fontStream = getClass().getResourceAsStream("/fonts/NanumGothic-Regular.ttf");
             var output = new ByteArrayOutputStream()) {
            if (fontStream == null) throw new IOException("PDF font is missing");
            var font = PDType0Font.load(doc, fontStream);
            var writer = new Writer(doc, font);
            writer.text("AIDLINK | 맞춤 정책 브리핑", 19, true);
            writer.text("평가일 " + report.content().evaluationDate() + " | 생성 당시의 저장 결과", 10, false);
            writer.gap(12);
            writer.text(report.content().guidance(), 10, false);
            writer.text("평가 정책 " + report.content().evaluatedCount() + "개 / 수록 정책 " + report.content().items().size() + "개", 10, false);
            if (report.content().candidateLimitReached()) writer.text("조건 미구조화 정책은 일부만 검토했습니다. 무료 검색에서 더 확인할 수 있습니다.", 10, false);
            int number=0;
            if (report.content().items().isEmpty()) writer.text("이번 브리핑에 담을 정책이 없습니다. 프로필과 공식 공고를 확인해 주세요.", 12, false);
            for (var item : report.content().items()) {
                writer.gap(16);
                writer.ensure(105);
                writer.text(++number + ". " + item.name(), 14, true);
                writer.text(item.provider() + " | " + item.category() + " | " + status(item.status().name()), 10, true);
                writer.text(item.reason(), 10, false);
                for (String check : item.checkConditions()) writer.text("- " + check, 10, false);
                String dates = item.applicationStart() == null && item.applicationEnd() == null
                        ? item.applicationPeriod().kind().name().equals("YEAR_ROUND") ? "상시 접수 (공식 원문 확인)" : "확인 필요"
                        : Objects.toString(item.applicationStart(), "시작일 확인 필요") + " ~ " + Objects.toString(item.applicationEnd(), "종료일 확인 필요");
                writer.text("신청 기간: " + dates, 10, false);
                var period = item.applicationPeriod();
                if (period.startTime() != null || period.endTime() != null)
                    writer.text("접수 시각: " + Objects.toString(period.startTime(), "미확인") + " ~ " + Objects.toString(period.endTime(), "미확인") + " (한국시간)", 10, false);
                if (period.sourceText() != null && !period.sourceText().isBlank()) writer.text("원문 일정: " + period.sourceText(),10,false);
                if (period.reviewRequired()) writer.text("변경된 신청기간의 재확인이 필요합니다.",10,true);
                writer.link("공식 원문", item.sourceUrl());
                writer.link("신청 안내", item.applicationUrl());
            }
            writer.finish();
            doc.getDocumentInformation().setTitle("AIDLINK 맞춤 정책 브리핑");
            doc.getDocumentInformation().setCreator("AIDLINK");
            doc.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
    }

    private String status(String status) {
        return switch (status) { case "ELIGIBLE" -> "조건 충족 가능성"; case "NEAR_MISS" -> "부족 조건 확인"; default -> "추가 확인 필요"; };
    }

    private static final class Writer {
        private static final float MARGIN=44, WIDTH=PDRectangle.A4.getWidth()-MARGIN*2;
        private final PDDocument doc;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        Writer(PDDocument doc, PDType0Font font) throws IOException { this.doc=doc; this.font=font; newPage(); }
        void newPage() throws IOException {
            if (stream!=null) stream.close();
            page=new PDPage(PDRectangle.A4); doc.addPage(page);
            stream=new PDPageContentStream(doc,page); y=PDRectangle.A4.getHeight()-MARGIN;
        }
        void ensure(float height) throws IOException { if (y-height<MARGIN+22) newPage(); }
        void gap(float height) { y-=height; }
        void text(String raw,float size,boolean emphasis) throws IOException {
            String value=clean(raw);
            for (String paragraph:value.split("\\R",-1)) {
                StringBuilder line=new StringBuilder();
                for (int cp:paragraph.codePoints().toArray()) {
                    String character=new String(Character.toChars(cp));
                    if (font.getStringWidth(line+character)/1000*size>WIDTH && !line.isEmpty()) {
                        line(line.toString(),size,emphasis,null); line.setLength(0);
                    }
                    line.append(character);
                }
                line(line.toString(),size,emphasis,null);
            }
        }
        void link(String label,String url) throws IOException {
            String safe=io.aidlink.policy.application.PublicPolicyLink.usableUrl(url);
            if (safe.isEmpty()) { text(label+": 링크 확인 필요",9,false); return; }
            // Use short labels for annotations; retain the full URL as wrapped, selectable text.
            line(label+" (클릭하여 열기)",10,true,safe);
            text(safe,8,false);
        }
        private void line(String text,float size,boolean emphasis,String uri) throws IOException {
            ensure(size*1.6f);
            stream.beginText(); stream.setFont(font,size);
            stream.setNonStrokingColor(emphasis?new Color(31,65,84):new Color(55,65,72));
            stream.newLineAtOffset(MARGIN,y); stream.showText(text); stream.endText();
            if (uri!=null) {
                var link=new PDAnnotationLink();
                link.setRectangle(new PDRectangle(MARGIN,y-2,font.getStringWidth(text)/1000*size,size+4));
                var border=new PDBorderStyleDictionary(); border.setWidth(0); link.setBorderStyle(border);
                var action=new PDActionURI(); action.setURI(uri); link.setAction(action); page.getAnnotations().add(link);
            }
            y-=size*1.6f;
        }
        private String clean(String raw) throws IOException {
            StringBuilder result=new StringBuilder();
            for (int cp:Objects.toString(raw,"").codePoints().toArray()) {
                if (cp=='\n') { result.append('\n'); continue; }
                if (Character.isISOControl(cp)) { result.append(' '); continue; }
                try { font.encode(new String(Character.toChars(cp))); result.appendCodePoint(cp); }
                catch (IllegalArgumentException ex) { result.append('?'); }
            }
            return result.toString();
        }
        void finish() throws IOException {
            stream.close(); stream=null;
            for (int i=0;i<doc.getNumberOfPages();i++) {
                try (var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true)) {
                    footer.beginText(); footer.setFont(font,8); footer.setNonStrokingColor(Color.GRAY);
                    footer.newLineAtOffset(MARGIN,28); footer.showText("AIDLINK | "+(i+1)+" / "+doc.getNumberOfPages()+" | 최종 자격은 공식 기관에서 확인하세요."); footer.endText();
                }
            }
        }
    }
}

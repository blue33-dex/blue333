package com.blue333.ordermgmt.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P1-T39 · NFR-S1：入库前 XSS 清洗——常见 payload 必须退化为纯文本或空串。 */
class XSSCleanerTest {

  @Test
  void removesScriptTagsButKeepsHarmlessText() {
    String cleaned = XSSCleaner.clean("<script>alert(1)</script>");

    // 任何尖括号都不允许残留，否则前端一渲染就会重新变成可执行标记
    assertThat(cleaned).doesNotContain("<").doesNotContain(">");
  }

  @Test
  void removesSelfContainedEventPayloadCompletely() {
    String cleaned = XSSCleaner.clean("<img src=x onerror=alert(1)>");

    assertThat(cleaned).isBlank();
    assertThat(cleaned).doesNotContain("onerror");
  }

  @Test
  void keepsInnerTextButDropsInlineMarkupAndAttributes() {
    String cleaned = XSSCleaner.clean("<b onclick=\"alert(1)\">加粗说明</b>");

    assertThat(cleaned).isEqualTo("加粗说明");
  }

  @Test
  void dropsJavascriptHrefWhenAnchorIsStripped() {
    String cleaned = XSSCleaner.clean("<a href=\"javascript:alert(1)\">点击</a>");

    assertThat(cleaned).isEqualTo("点击");
    assertThat(cleaned).doesNotContain("javascript");
  }

  @Test
  void handlesMixedCaseAndNestedTags() {
    String cleaned = XSSCleaner.clean("<ScRiPt>alert(1)</sCrIpT><div><span>需求备注</span></div>");

    assertThat(cleaned).doesNotContain("<").doesNotContain(">");
  }

  @Test
  void leavesPlainTextUntouched() {
    assertThat(XSSCleaner.clean("600mm × 900mm 海报，主色 #0055A4")).isEqualTo("600mm × 900mm 海报，主色 #0055A4");
  }

  @Test
  void returnsNullForNullInput() {
    assertThat(XSSCleaner.clean(null)).isNull();
  }

  @Test
  void returnsEmptyStringForEmptyTagPayload() {
    assertThat(XSSCleaner.clean("<>")).isEmpty();
  }
}

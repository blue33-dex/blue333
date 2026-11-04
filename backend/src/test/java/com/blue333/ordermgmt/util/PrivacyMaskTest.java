package com.blue333.ordermgmt.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P1-T39 · NFR-S2/S3：手机号与邮箱在非授权视图输出前必须脱敏。 */
class PrivacyMaskTest {

  // --- 手机号：13812348888 → 138****8888 ---

  @Test
  void masksMainlandChinaMobileNumber() {
    assertThat(PrivacyMask.maskPhone("13812348888")).isEqualTo("138****8888");
  }

  @Test
  void keepsOnlyTheFirstThreeAndLastFourDigits() {
    String masked = PrivacyMask.maskPhone("13900001111");

    assertThat(masked).isEqualTo("139****1111");
    assertThat(masked).doesNotContain("0000");
  }

  @Test
  void leavesShorterOrLongerNumbersUnchanged() {
    assertThat(PrivacyMask.maskPhone("1381234888")).isEqualTo("1381234888");
    assertThat(PrivacyMask.maskPhone("+8613812348888")).isEqualTo("+8613812348888");
    assertThat(PrivacyMask.maskPhone("13812348888x")).isEqualTo("13812348888x");
  }

  @Test
  void leavesNonNumericTextUnchanged() {
    assertThat(PrivacyMask.maskPhone("座机：021-88889999")).isEqualTo("座机：021-88889999");
  }

  @Test
  void returnsNullForNullPhone() {
    assertThat(PrivacyMask.maskPhone(null)).isNull();
  }

  // --- 邮箱：zhangming@example.com → z***@example.com ---

  @Test
  void masksEmailLocalPart() {
    assertThat(PrivacyMask.maskEmail("zhangming@example.com")).isEqualTo("z***@example.com");
  }

  @Test
  void keepsDomainIntactIncludingSubDomain() {
    assertThat(PrivacyMask.maskEmail("li.wei@mail.example.com.cn"))
        .isEqualTo("l***@mail.example.com.cn");
  }

  @Test
  void preservesOriginalCase() {
    assertThat(PrivacyMask.maskEmail("ZhangMing@Example.COM")).isEqualTo("Z***@Example.COM");
  }

  @Test
  void leavesInvalidEmailUnchanged() {
    assertThat(PrivacyMask.maskEmail("not-an-email")).isEqualTo("not-an-email");
    assertThat(PrivacyMask.maskEmail("zhangming@example")).isEqualTo("zhangming@example");
    assertThat(PrivacyMask.maskEmail("@example.com")).isEqualTo("@example.com");
  }

  @Test
  void returnsNullForNullEmail() {
    assertThat(PrivacyMask.maskEmail(null)).isNull();
  }
}

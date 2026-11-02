package com.blue333.ordermgmt.util;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Masks sensitive contact data before it leaves an authorized API boundary. */
@Component
public final class PrivacyMask {
  private static final Pattern MAINLAND_CHINA_PHONE = Pattern.compile("^1\\d{10}$");
  private static final Pattern EMAIL = Pattern.compile(
      "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
          + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
          + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

  public PrivacyMask() {
  }

  /** Returns a Chinese mainland phone number as 138****8888; non-phone input is unchanged. */
  public static String maskPhone(String phone) {
    if (phone == null || !MAINLAND_CHINA_PHONE.matcher(phone).matches()) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(7);
  }

  /** Returns an email as z***@example.com; non-email input is unchanged. */
  public static String maskEmail(String email) {
    if (email == null || !EMAIL.matcher(email).matches()) {
      return email;
    }

    int atIndex = email.indexOf('@');
    return email.substring(0, 1) + "***" + email.substring(atIndex);
  }
}

package com.blue333.ordermgmt.aop;

import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records an audited business action.
 *
 * <p>Expression attributes use Spring Expression Language. Method arguments are available as
 * {@code #p0}, {@code #a0}, {@code #arg0}, and, where Java parameter metadata is available, by
 * parameter name. On successful execution {@code #result} is also available; on failure
 * {@code #exception} is available. {@link #targetId()} must evaluate to a positive numeric ID.
 * Empty optional expressions produce a null audit field.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedAction {
  AuditActionType action();

  AuditTargetType targetType();

  /** Required SpEL expression resolving to the persisted target's positive numeric identifier. */
  String targetId();

  /** Optional SpEL expression evaluated before the business method. */
  String beforeState() default "";

  /** Optional SpEL expression evaluated after a successful business method. */
  String afterState() default "";

  /** Optional SpEL expression evaluated after the method outcome is known. */
  String remark() default "";
}

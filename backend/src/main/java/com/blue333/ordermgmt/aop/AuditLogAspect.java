package com.blue333.ordermgmt.aop;

import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.service.AuditLogService;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/** Captures annotation-defined audit values around proxied Spring business methods. */
@Aspect
@Component
public class AuditLogAspect {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogAspect.class);
  private static final String SUCCESS = "SUCCESS";
  private static final String FAILURE = "FAILURE";

  private final AuditLogService auditLogService;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

  public AuditLogAspect(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @Around("@annotation(auditedAction)")
  public Object audit(ProceedingJoinPoint joinPoint, AuditedAction auditedAction) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Object[] arguments = joinPoint.getArgs();
    Long targetId = resolveTargetId(auditedAction.targetId(), method, arguments, null, null);
    String beforeState = evaluate(auditedAction.beforeState(), method, arguments, null, null);

    try {
      Object result = joinPoint.proceed();
      Long resolvedTargetId = targetId != null ? targetId
          : resolveTargetId(auditedAction.targetId(), method, arguments, result, null);
      String afterState = evaluate(auditedAction.afterState(), method, arguments, result, null);
      record(auditedAction, resolvedTargetId, beforeState, afterState, SUCCESS,
          evaluate(auditedAction.remark(), method, arguments, result, null));
      return result;
    } catch (Throwable exception) {
      record(auditedAction, targetId, beforeState, null, FAILURE,
          failureRemark(evaluate(auditedAction.remark(), method, arguments, null, exception), exception));
      throw exception;
    }
  }

  private void record(
      AuditedAction auditedAction,
      Long targetId,
      String beforeState,
      String afterState,
      String result,
      String remark) {
    if (targetId == null) {
      LOGGER.warn("Skipping audit action {} because its targetId expression could not resolve a positive ID",
          auditedAction.action());
      return;
    }
    try {
      auditLogService.record(auditedAction.action(), auditedAction.targetType(), targetId,
          beforeState, afterState, result, remark);
    } catch (RuntimeException exception) {
      // The async proxy normally returns immediately; protect the business operation if dispatch fails.
      LOGGER.error("Unable to dispatch audit action {}", auditedAction.action(), exception);
    }
  }

  private Long resolveTargetId(
      String expression, Method method, Object[] arguments, Object result, Throwable exception) {
    String value = evaluate(expression, method, arguments, result, exception);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      long targetId = Long.parseLong(value);
      return targetId > 0 ? targetId : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String evaluate(
      String expression, Method method, Object[] arguments, Object result, Throwable exception) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      StandardEvaluationContext context = new StandardEvaluationContext();
      for (int index = 0; index < arguments.length; index++) {
        context.setVariable("p" + index, arguments[index]);
        context.setVariable("a" + index, arguments[index]);
        context.setVariable("arg" + index, arguments[index]);
      }
      String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
      if (parameterNames != null) {
        for (int index = 0; index < parameterNames.length; index++) {
          context.setVariable(parameterNames[index], arguments[index]);
        }
      }
      context.setVariable("result", result);
      context.setVariable("exception", exception);
      Object value = expressionParser.parseExpression(expression).getValue(context);
      return value == null ? null : String.valueOf(value);
    } catch (RuntimeException evaluationFailure) {
      LOGGER.warn("Could not evaluate audit expression '{}'", expression, evaluationFailure);
      return null;
    }
  }

  private String failureRemark(String configuredRemark, Throwable exception) {
    String failureDetail = "Business operation failed: " + exception.getClass().getSimpleName();
    return configuredRemark == null || configuredRemark.isBlank()
        ? failureDetail
        : configuredRemark + "; " + failureDetail;
  }
}

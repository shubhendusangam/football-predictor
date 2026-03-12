package com.app.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AOP aspect that logs method entry, exit, and execution time for
 * controller and service classes across all modules.
 * <p>
 * Uses SLF4J as the logging facade so the underlying implementation
 * (Logback) is transparent. Activated automatically via
 * {@code spring-boot-starter-aop} on the classpath.
 * <p>
 * <b>What gets logged:</b>
 * <ul>
 *   <li>Controller methods — entry with arguments, exit with return + duration</li>
 *   <li>Service methods   — same, but only when running at DEBUG level</li>
 *   <li>Slow methods (&gt; 1 000 ms) — always logged at WARN regardless of level</li>
 * </ul>
 *
 * @see LogConstants#LOGGER_PERFORMANCE
 */
@Aspect
public class PerformanceLoggingAspect {

    private static final Logger perfLog =
            LoggerFactory.getLogger(LogConstants.LOGGER_PERFORMANCE);

    /** Threshold in milliseconds — anything slower triggers a WARN. */
    private static final long SLOW_THRESHOLD_MS = 1_000L;

    // ── Pointcuts ────────────────────────────────────────────────

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) " +
              "|| within(@org.springframework.stereotype.Controller *)")
    public void controllerMethods() { }

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() { }

    @Pointcut("execution(* com.app..*(..)) && (controllerMethods() || serviceMethods())")
    public void applicationMethods() { }

    // ── Advice ───────────────────────────────────────────────────

    @Around("applicationMethods()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String className = sig.getDeclaringType().getSimpleName();
        String methodName = sig.getName();
        String fullName = className + "." + methodName;

        Logger targetLog = LoggerFactory.getLogger(sig.getDeclaringType());

        // Log entry at DEBUG level
        if (targetLog.isDebugEnabled()) {
            targetLog.debug("▸ Entering {}({})", fullName, summarizeArgs(joinPoint.getArgs()));
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;

            // Log exit
            if (elapsed > SLOW_THRESHOLD_MS) {
                perfLog.warn("⚠ SLOW  {} completed in {}ms", fullName, elapsed);
            } else if (targetLog.isDebugEnabled()) {
                targetLog.debug("◂ Exiting {} ({}ms)", fullName, elapsed);
            }

            return result;

        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            targetLog.error("✖ {} failed after {}ms — {}: {}",
                    fullName, elapsed, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Produce a short summary of arguments suitable for a single log line.
     * Truncates long toString representations.
     */
    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            if (args[i] == null) {
                sb.append("null");
            } else {
                String repr = args[i].toString();
                sb.append(repr.length() > 80 ? repr.substring(0, 80) + "…" : repr);
            }
        }
        return sb.toString();
    }
}


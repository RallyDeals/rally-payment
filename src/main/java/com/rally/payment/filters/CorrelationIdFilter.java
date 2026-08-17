package com.rally.payment.filters;

import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component()
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(PaymentMessageHeaders.CORRELATION_ID);
        String traceId = request.getHeader(PaymentMessageHeaders.TRACE_ID);

        if(correlationId == null || correlationId.isBlank()){
            correlationId = UUID.randomUUID().toString();
        }

        if(traceId == null || traceId.isBlank()){
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(PaymentMessageHeaders.CORRELATION_ID,correlationId);
        MDC.put(PaymentMessageHeaders.TRACE_ID,traceId);


        response.addHeader(PaymentMessageHeaders.CORRELATION_ID,correlationId);
        response.addHeader(PaymentMessageHeaders.TRACE_ID,traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(PaymentMessageHeaders.CORRELATION_ID);
            MDC.remove(PaymentMessageHeaders.TRACE_ID);
        }



    }
}

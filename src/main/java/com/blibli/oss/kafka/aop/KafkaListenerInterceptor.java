package com.blibli.oss.kafka.aop;

import com.blibli.oss.kafka.helper.KafkaHelper;
import com.blibli.oss.kafka.interceptor.InterceptorUtil;
import com.blibli.oss.kafka.interceptor.KafkaConsumerInterceptor;
import com.blibli.oss.kafka.interceptor.events.ConsumerEvent;
import com.blibli.oss.kafka.properties.KafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eko Kurniawan Khannedy
 */
@Slf4j
public class KafkaListenerInterceptor implements MethodInterceptor, ApplicationContextAware, InitializingBean {

  private ApplicationContext applicationContext;

  private ObjectMapper objectMapper;

  private KafkaProperties.ModelProperties modelProperties;

  private List<KafkaConsumerInterceptor> kafkaConsumerInterceptors;

  public KafkaListenerInterceptor(ObjectMapper objectMapper, KafkaProperties.ModelProperties modelProperties) {
    this.objectMapper = objectMapper;
    this.modelProperties = modelProperties;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    kafkaConsumerInterceptors = InterceptorUtil.getKafkaConsumerInterceptors(applicationContext);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    if (isKafkaListener(invocation) && isConsumerRecordArgument(invocation)) {
      ConsumerRecord<String, String> record = getConsumerRecord(invocation);
      ConsumerEvent event = KafkaHelper.toConsumerEvent(record, getEventId(record));
      try {
        if (InterceptorUtil.fireBeforeConsume(kafkaConsumerInterceptors, event)) {
          return null; // cancel process
        } else {
          Object response = invocation.proceed();
          InterceptorUtil.fireAfterSuccessConsume(kafkaConsumerInterceptors, event);
          return response;
        }
      } catch (Throwable throwable) {
        InterceptorUtil.fireAfterErrorConsume(kafkaConsumerInterceptors, event, throwable);
        throw throwable;
      }
    } else {
      return invocation.proceed();
    }
  }

  private boolean isKafkaListener(MethodInvocation invocation) {
    Method method = invocation.getMethod();
    return AnnotationUtils.findAnnotation(method, KafkaListener.class) != null;
  }

  private boolean isConsumerRecordArgument(MethodInvocation invocation) {
    Object[] arguments = invocation.getArguments();
    if (arguments == null || arguments.length == 0) {
      return false;
    }

    return Arrays.stream(invocation.getArguments())
        .anyMatch(o -> o instanceof ConsumerRecord);
  }

  @SuppressWarnings("unchecked")
  private ConsumerRecord<String, String> getConsumerRecord(MethodInvocation invocation) {
    return (ConsumerRecord<String, String>) Arrays.stream(invocation.getArguments())
        .filter(o -> o instanceof ConsumerRecord)
        .findFirst()
        .orElse(null);
  }

  private String getEventId(ConsumerRecord<String, String> record) {
    return KafkaHelper.getEventId(record.value(), objectMapper, modelProperties);
  }
}
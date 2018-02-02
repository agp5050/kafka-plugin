/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blibli.oss.kafka.sleuth;

import com.blibli.oss.kafka.interceptor.events.ConsumerEvent;
import com.blibli.oss.kafka.interceptor.events.ProducerEvent;
import com.blibli.oss.kafka.properties.KafkaProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Eko Kurniawan Khannedy
 */
public class SleuthSpanInterceptorTest {

  public static final String SPAN = "span";
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private KafkaProperties.ModelProperties modelProperties;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private Tracer tracer;

  private SampleData sampleData = SampleData.builder()
      .build();

  private ProducerEvent producerEvent = ProducerEvent.builder()
      .value(sampleData)
      .build();

  private ConsumerEvent consumerEvent = ConsumerEvent.builder()
      .build();

  private SleuthSpanInterceptor sleuthSpanInterceptor;

  @Before
  public void setUp() throws Exception {
    sleuthSpanInterceptor = new SleuthSpanInterceptor(modelProperties, objectMapper, tracer);

    when(modelProperties.getTrace()).thenReturn(SPAN);
  }

  @Test
  public void beforeSend() {
    sleuthSpanInterceptor.beforeSend(producerEvent);
    assertEquals(sampleData.getSpan(), Collections.emptyMap());
    verify(tracer, times(1)).getCurrentSpan();
  }

  @Test
  public void beforeConsume() throws JsonProcessingException {
    Span span = Span.builder()
        .processId("processId")
        .spanId(41841094L)
        .traceId(234248923L)
        .name("name")
        .build();

    Map<String, String> spanMap = SleuthHelper.toMap(span);

    Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put("span", spanMap);

    String json = objectMapper.writeValueAsString(jsonMap);

    consumerEvent.setValue(json);

    sleuthSpanInterceptor.beforeConsume(consumerEvent);

    verify(tracer, times(1)).continueSpan(any(Span.class));
  }

  @Data
  @Builder
  private static class SampleData {

    private Map<String, String> span = Collections.singletonMap("Test", "Test");
  }
}
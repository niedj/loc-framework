package com.loc.framework.autoconfigure.test.springmvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.Lists;
import com.loc.framework.autoconfigure.springmvc.LocAccessLogFilter;
import com.loc.framework.autoconfigure.springmvc.LocSpringMvcProperties;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created on 2017/12/1.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class LocAccessLogTest {

  @Autowired
  private MappingJackson2HttpMessageConverter jackson2HttpMessageConverter;

  @Autowired
  private StreamMessageConverter streamMessageConverter;

  private MockMvc requestMockMvc;
  private MockMvc bothMockMvc;

  @Before
  public void setUp() throws Exception {
    LocSpringMvcProperties requestProperties = new LocSpringMvcProperties();
    requestProperties.setResponseBodyLength(1024);

    this.requestMockMvc = MockMvcBuilders
        .standaloneSetup(new GetController(), new StreamController())
        .setMessageConverters(jackson2HttpMessageConverter, streamMessageConverter)
        .addFilters(new LocAccessLogFilter(requestProperties))
        .build();

    LocSpringMvcProperties bothProperties = new LocSpringMvcProperties();
    bothProperties.setResponseBodyLength(1024);
    bothProperties.setIncludeResponse(true);
    bothMockMvc = MockMvcBuilders
        .standaloneSetup(new GetController(), new StreamController())
        .setMessageConverters(jackson2HttpMessageConverter, streamMessageConverter)
        .addFilters(new LocAccessLogFilter(bothProperties)).build();
  }

  @Test
  public void getTest1() throws Exception {
    this.requestMockMvc
        .perform(get("/get/test1").header("header-key", "header-value").accept("application/json"))
        .andExpect(status().isOk()).andReturn();

    this.bothMockMvc
        .perform(get("/get/test1").header("header-key", "header-value").accept("application/json"))
        .andExpect(status().isOk()).andReturn();

    this.bothMockMvc
        .perform(get("/actuator/info").header("header-key", "header-value").accept("application/json"))
        .andExpect(status().isOk()).andReturn();
  }

  @Test
  public void getSleep() throws Exception {
    this.requestMockMvc.perform(get("/get/sleep?time=1000").accept("application/json"))
        .andExpect(status().isOk()).andReturn();

    this.bothMockMvc.perform(get("/get/sleep?time=1000").accept("application/json"))
        .andExpect(status().isOk()).andReturn();
  }

  @Test
  public void getStream() throws Exception {
    this.requestMockMvc.perform(get("/get/octetStream").accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(status().isOk())
        .andReturn();

    this.bothMockMvc.perform(get("/get/octetStream").accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(status().isOk()).andReturn();
  }

  @Test
  public void getDemo() throws Exception {
    this.requestMockMvc.perform(
        get("/get/demo")
            .param("name", "thomas")
            .param("age", "29")
            .param("address", "a1", "a2")
            .accept("application/json"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andExpect(jsonPath("$.name" ).value("thomas"))
        .andExpect(jsonPath("$.age" ).value("29"))
        .andExpect(jsonPath("$.address" ).value(Lists.newArrayList("a1", "a2")))
        .andReturn();

    this.bothMockMvc.perform(
        get("/get/demo")
            .param("name", "thomas")
            .param("age", "29")
            .param("address", "a1", "a2")
            .accept("application/json"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andExpect(jsonPath("$.name" ).value("thomas"))
        .andExpect(jsonPath("$.age" ).value("29"))
        .andExpect(jsonPath("$.address" ).value(Lists.newArrayList("a1", "a2")))
        .andReturn();
  }

  @Test
  public void getMaxPlayload() throws Exception {
    this.requestMockMvc.perform(
        get("/get/maxPlayload")
            .param("times", "200")
            .accept("application/json"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andExpect(jsonPath("$.name" ).value("thomas"))
        .andExpect(jsonPath("$.age" ).value("29"))
        .andReturn();

    this.bothMockMvc.perform(
        get("/get/maxPlayload")
            .param("times", "200")
            .accept("application/json"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andExpect(jsonPath("$.name" ).value("thomas"))
        .andExpect(jsonPath("$.age" ).value("29"))
        .andReturn();
  }


  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Demo {
    private String name;
    private int age;
    private List<String> address;
  }

  @MinimalWebConfiguration
  @Controller
  public static class StreamController {

    @GetMapping(value = "/get/octetStream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStream> responseOctetStream() throws IOException {
      File tmpFile = new File("/tmp/" + UUID.randomUUID().toString() + ".log");
      tmpFile.createNewFile();
      Path path = Paths.get(tmpFile.getAbsolutePath());
      ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(path));
      return ResponseEntity.ok()
          .contentLength(tmpFile.length())
          .contentType(MediaType.parseMediaType("application/octet-stream"))
          .body(resource.getInputStream());
    }
  }

  @MinimalWebConfiguration
  @RestController
  public static class GetController {

    @GetMapping(value = "/get/test1")
    public String responsePlainTest() {
      return "OK";
    }

    @GetMapping(value = "/actuator/info")
    public String info() {
      return "OK";
    }

    @GetMapping(value = "/get/sleep")
    public String responseSleep(
        @RequestParam(value = "time")
            long time) {
      try {
        Thread.sleep(time);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return String.valueOf(time);
    }

    @GetMapping(value = "/get/demo")
    public Demo responseDemo(
        @RequestParam(value = "name")
            String name,
        @RequestParam(value = "age")
            int age,
        @RequestParam(value = "address")
            List<String> address) {
      Demo demo = new Demo();
      demo.setName(name);
      demo.setAge(age);
      demo.setAddress(address);
      return demo;
    }

    @GetMapping(value = "/get/maxPlayload")
    public Demo responseDemo(
        @RequestParam(value = "times")
            long times) {
      Demo demo = new Demo();
      demo.setName("thomas");
      demo.setAge(29);
      List<String> address = Lists.newArrayList();
      for(int i = 0; i < times; i++) {
        address.add("a" + i);
      }
      demo.setAddress(address);
      return demo;
    }
  }


  static class StreamMessageConverter extends AbstractHttpMessageConverter<InputStream> {

    public StreamMessageConverter(MediaType supportedMediaType) {
      super(supportedMediaType);
    }
    @Override
    protected boolean supports(Class<?> clazz) {
      return clazz.isAssignableFrom(ByteArrayInputStream.class);
    }

    @Override
    protected InputStream readInternal(Class<? extends InputStream> clazz,
        HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      return inputMessage.getBody();
    }

    @Override
    protected void writeInternal(InputStream inputStream, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {
      IOUtils.copy(inputStream, outputMessage.getBody());
    }
  }

  @Configuration
  public static class WebConfig {

    @Bean
    public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter() {
      MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
      Jackson2ObjectMapperBuilder builder = this.jacksonBuilder();
      converter.setObjectMapper(builder.build());
      return converter;
    }

    @Bean
    public StreamMessageConverter streamMessageConverter() {
      return new StreamMessageConverter(MediaType.APPLICATION_OCTET_STREAM);
    }



    public Jackson2ObjectMapperBuilder jacksonBuilder() {
      return new Jackson2ObjectMapperBuilder();
    }
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @Configuration
  @Import({
      ServletWebServerFactoryAutoConfiguration.class,
      JacksonAutoConfiguration.class
  })
  protected @interface MinimalWebConfiguration {

  }

}
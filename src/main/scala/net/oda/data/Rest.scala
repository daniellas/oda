package net.oda.data

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.HashMap
import java.util.Map

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate
import com.empirica.rest.client.spring.RestTemplateHttpExecutor

object Rest {
	private val restTemplate = new RestTemplate()

	restTemplate.setMessageConverters(Collections.singletonList(new StringHttpMessageConverter(StandardCharsets.UTF_8)))

	val jsonHeaders: Map[String, java.util.List[String]] = new HashMap();

	jsonHeaders.put(HttpHeaders.CONTENT_TYPE, Collections.singletonList(MediaType.APPLICATION_JSON_UTF8_VALUE))
	jsonHeaders.put(HttpHeaders.ACCEPT, Collections.singletonList(MediaType.APPLICATION_JSON_UTF8_VALUE))

	val httpExecutor = RestTemplateHttpExecutor.of(restTemplate);

}
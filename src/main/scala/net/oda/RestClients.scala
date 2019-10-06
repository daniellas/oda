package net.oda

import java.util.{Collections, HashMap, Map}

import org.apache.http.HttpHeaders

object RestClients {
  val jsonHeaders: Map[String, java.util.List[String]] = new HashMap();

  jsonHeaders.put(HttpHeaders.ACCEPT, Collections.singletonList("application/json"))
}

package io.github.zhyou.starter.web;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;

@Path("/web")
public class DemoWebResource {
  @GET
  @Path("/demo")
  @Produces(MediaType.TEXT_HTML)
  public String demo() {
    return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>Quarkus Demo</title><h1>Quarkus Starter</h1><p>示例服务已启动。</p></html>";
  }

  @GET
  @Path("/redirect")
  public Response redirect() {
    return Response.seeOther(URI.create("/web/demo")).build();
  }
}

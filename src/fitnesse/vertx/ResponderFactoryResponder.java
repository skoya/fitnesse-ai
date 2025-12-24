package fitnesse.vertx;

import fitnesse.FitNesseContext;
import fitnesse.Responder;
import fitnesse.http.Request;
import fitnesse.http.Response;

final class ResponderFactoryResponder implements Responder {
  @Override
  public Response makeResponse(FitNesseContext context, Request request) throws Exception {
    Responder responder = context.responderFactory.makeResponder(request);
    return responder.makeResponse(context, request);
  }
}

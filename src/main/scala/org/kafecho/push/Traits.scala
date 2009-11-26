package org.kafecho.push

import org.apache.commons.logging.{Log,LogFactory}
import org.restlet.{Client,Component,Restlet};
import org.restlet.data._

/**
 * A logging trait that adds Apache commons logging support to all classes which mix it in. 
 */
trait Logging{
  val log = LogFactory.getLog(getClass)
}

/**
 * A Restlet which does handle GET requests and nothing else.
 */
trait GET extends Restlet{
  override def handle(request : Request, response: Response) = if (request.getMethod == Method.GET) doGET(request,response) else super.handle(request,response)
  def doGET(request : Request, response : Response)
}

/**
 * A Restlet which does handle POST requests and nothing else.
 */
trait POST extends Restlet{
  override def handle(request : Request, response: Response) = if (request.getMethod == Method.POST) doPOST(request,response) else super.handle(request,response)
  def doPOST(request : Request, response : Response)
}

/**
 * A Restlet which does handle DELETE requests and nothing else.
 */
trait DELETE extends Restlet{
  override def handle(request : Request, response: Response) = if (request.getMethod == Method.DELETE) doDELETE(request,response) else super.handle(request,response)
  def doDELETE(request : Request, response : Response)
}

/**
 * A Restlet which does not handle any requests.
 */
trait Default extends Restlet{
  override def handle(request : Request, response: Response){
    response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED,"Method not supported:" + request.getMethod.getDescription)
  }
}

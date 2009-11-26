package org.kafecho.push

import org.restlet.{Client,Component,Restlet};
import org.restlet.data._
import java.text.SimpleDateFormat

import scala.xml.XML
import scala.xml.Elem

/*
 * A Scala singleton object that can parse dates used by the Atom format. 
*/
object RFC3339 extends SimpleDateFormat("yyyy-MM-dd'T'h:m:ss")

/**
 * The admin restlet provides a simple admin API to add / list and delete subscriptions.
 * This allows for a variety of front end interfaces, such as command line with curl or web based using GWT for instance.
 * This restlet mixes in the traits corresponding to the HTTP methods it supports and the dispatch logic is handled by each of the trait.
 */
class AdminRestlet (val subscriber : Subscriber ) extends Default with Logging with POST with GET with DELETE{
	def doPOST(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id != null){
		  response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
		  return
		}
		if (request.getEntity.getMediaType != MediaType.TEXT_XML){
		  response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE,"Please provide some XML.")
		  return
		}
		
		val xml = XML.loadString(request.getEntity.getText)
		subscriber.subUnsub(xml \ "@feedURL" text , true)
	}
	
	def doDELETE(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id == null) response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
		else{
		  val found = subscriber.activeSubscriptions.find( _.id.toString == id)
		  found match{
		    case Some(subscription) =>
		    	subscriber.subUnsub(subscription.feedURL,subscription.topicURL,subscription.hubURL,false)
		    	response.setStatus(Status.SUCCESS_OK,"Created a new subscription for feed: " + subscription.feedURL)
		    case None => response.setStatus(Status.CLIENT_ERROR_NOT_FOUND)	
		  }
		}
	}
	def doGET(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id != null){
		  val found = subscriber.activeSubscriptions.find( _.id.toString == id)
		  found match{
		    case Some(subscription) => response.setEntity(subscription.toXML.toString, MediaType.TEXT_XML)
		    case None => response.setStatus(Status.CLIENT_ERROR_NOT_FOUND)	
		  }
		}
		else response.setEntity ( subscriber.subscriptionsToXML.toString,MediaType.TEXT_XML)
	}
}

/**
 * A PuSH restlet implements the REST web services of the Subscriber component in the PuSH protocol.
 * Complies to the specs http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.2.html
 */
class PuSHRestlet (val subscriber : Subscriber) extends Default with Logging with GET with POST{
	/**
	 * Handle subscribe / unsubscribe challenges from a hub.
	 * TODO: add checks to ensure subscriber does not blindly accept challenges from hub.	 
	 */
	def doGET(request: Request, response:Response){
	  	val params 	= request.getResourceRef.getQueryAsForm
	  	val mode 	= params.getFirstValue(Constants.mode)
	  	val topic	= params.getFirstValue(Constants.topic)
	  	val lease_seconds = params.getFirstValue(Constants.lease_seconds).toInt
	  	
	  	log.info("Received hub challenge:\n-Mode: " + mode + "\n-Topic: " + topic + "\n-Lease: " + lease_seconds + " seconds")
		response.setEntity(params.getFirstValue(Constants.challenge),MediaType.TEXT_PLAIN)
	
		mode match{
		  case Constants.subscribe 	 => subscriber.added(topic) 
		  case Constants.unsubscribe => subscriber.removed(topic)
		}
  
  		log.info("Acknowledged hub challenge.")
	}

	/*
	 * Handle content notifications from a hub.
	 */
	def  doPOST(request: Request,response:Response){
		val entity = request.getEntity
		if (entity.getMediaType == MediaType.APPLICATION_ATOM_XML){
			response.setStatus (Status.SUCCESS_OK)
			val atom = XML.load(entity.getStream)
			processPost(atom)
		}
		else{
			response.setStatus (Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE)
   			log.error("Received hub notification containing unsupported payload of type " + entity.getMediaType + "\n" + entity.getText)
		}
	}
 
	/*
	* Extract some useful info from a post for display on the screen.	 	
	*/
	def processPost(atom : Elem){
		val Some(topicNode) = (atom\"link").find( _\"@rel" == "self")
		println (RFC3339.parse((atom\"updated").text) + " --- Update from: " + (atom\"title").text )
		println ("Topic URL: " + topicNode\"@href" + "\n")
  
		(atom\"entry").foreach{ e=>
			val link = (e\"link").find( _\"@rel" == "alternate").get\"@href"
			val entryTitle = (e\"title").text
			val entryPublished = (e\"published").text
			println (entryTitle + " (published " + RFC3339.parse(entryPublished) + ")\n" + link + "\n")
		}
	}
}

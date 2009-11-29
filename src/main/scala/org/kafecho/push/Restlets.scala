/*
 * Copyright 2009 Guillaume Belrose
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
*/
package org.kafecho.push

import org.restlet.{Client,Component,Restlet};
import org.restlet.data._
import java.text.SimpleDateFormat

import scala.xml.{XML,PrettyPrinter}
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
    val xmlPrinter 	= new PrettyPrinter(100,2)

	def doPOST(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id != null){
		  response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
		  return
		}
		
		if (request.getEntity.getMediaType != MediaType.APPLICATION_WWW_FORM){
		  log.error("Invalid MIME type: " + request.getEntity.getMediaType)
		  response.setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE)
		  return
		}
		val form = request.getEntityAsForm
		val feedURL = form.getFirstValue("feedURL")
		if (feedURL != null){
			subscriber.subUnsub(feedURL,true)
			response.setEntity("Creating subscription for feed: " + feedURL,MediaType.TEXT_PLAIN)
			response.setStatus(Status.SUCCESS_ACCEPTED)
		}
}
	
	def doDELETE(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id == null) response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
		else{
		  val found = subscriber.activeSubscriptions.find( _.id == id)
		  found match{
		    case Some(feed) =>
		    	subscriber.subUnsub(feed,false)
       			response.setEntity("Removing subscription for feed: " +  feed, MediaType.TEXT_PLAIN)
       			response.setStatus(Status.SUCCESS_ACCEPTED)
		    case None => response.setStatus(Status.CLIENT_ERROR_NOT_FOUND)	
		  }
		}
	}
	def doGET(request : Request, response : Response){
		val id = request.getAttributes.get("ID")
		if (id != null){
		  val found = subscriber.activeSubscriptions.find( _.id == id)
		  found match{
		    case Some(feed) => response.setEntity(xmlPrinter.format(feed.toXML), MediaType.TEXT_XML)
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
	 */
	def doGET(request: Request, response:Response){
	  	val params 	= request.getResourceRef.getQueryAsForm
	  	val mode 	= params.getFirstValue(Constants.mode)
	  	val topic	= params.getFirstValue(Constants.topic)
	  	val lease_seconds = params.getFirstValue(Constants.lease_seconds).toInt
	  	
	  	log.info("Received hub challenge:\n-Mode: " + mode + "\n-Topic: " + topic + "\n-Lease: " + lease_seconds + " seconds")

	  	// TODO: add appropriate checks to ensure the subscriber does not blindly accept challenges from hub.	 
	  	response.setEntity(params.getFirstValue(Constants.challenge),MediaType.TEXT_PLAIN)

		mode match{
		  case Constants.subscribe 	 => subscriber.subscribed(topic) 
		  case Constants.unsubscribe => subscriber.unsubscribed(topic)
		}
  
  		log.info("Acknowledged hub challenge.")
	}

	/*
	 * Handle content notification from a hub.
	 */
	def  doPOST(request: Request,response:Response){
	    log.info("Incoming POST request: " + request.getResourceRef)
		val entity = request.getEntity
		if (entity.getMediaType == MediaType.APPLICATION_ATOM_XML){
			response.setStatus (Status.SUCCESS_OK)
			val atom = XML.load(entity.getStream)
			subscriber.contentPublished(atom)
		}
		else{
   			log.error("Received hub notification containing unsupported payload of type " + entity.getMediaType + "\n" + entity.getText)
			response.setStatus (Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE)
		}
	}
}

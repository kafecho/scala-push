package org.kafecho.push

import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.Map

import java.text.SimpleDateFormat

import org.apache.commons.logging.{Log,LogFactory}
import org.restlet.{Client,Component,Restlet};
import org.restlet.data._

/**
 * A simple client for the PubSubHubBub protocol.
 * Complies to the specs http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.2.html
 * Author: Guillaume Belrose
 */

/**
 * A logging trait that adds Apache commons logging support to all classes which mix it in. 
 */
trait Logging{
  val log = LogFactory.getLog(getClass)
}

/**
 * A Scala singleton object which contains useful constants.
 */
object Constants{
    val callback= "hub.callback"
    val mode	= "hub.mode"
    val subscribe ="subscribe"
    val unsubscribe ="unsubscribe"
    val topic 	= "hub.topic"
    val verify 	= "hub.verify"
    val lease_seconds = "hub.lease_seconds"
    val secret  = "hub.secret"
    val verify_token = "hub.verify_token"
    val sync  = "sync"
    val async = "async"
    val challenge = "hub.challenge"
}

/*
 * A Scala singleton object that can parse dates used by the Atom format. 
*/
object RFC3339 extends SimpleDateFormat("yyyy-MM-dd'T'h:m:ss")

/**
 * A Restlet that handles GET and POST requests from a hub.
 */
class SubscriberRestlet extends Restlet with Logging{
	
	override def handle(request : Request, response : Response){
		log.info("Incoming request: " + request.getResourceRef)
		if (request.getMethod == Method.GET) doGET(request,response)
		else if (request.getMethod == Method.POST) doPOST(request, response)
		else response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED,"Method not supported:" + request.getMethod.getDescription)
	}

	/**
	 * Handle subscribe / unsubscribe challenges from a hub.
	 * TODO: add checks to ensure subscriber does not blindly accept challenges from hub.	 
	 */
	def doGET(request: Request, response:Response){
	  	val params = request.getResourceRef.getQueryAsForm
	  	val mode = params.getFirstValue(Constants.mode)
	  	val topic= params.getFirstValue(Constants.topic)
	  	val lease_seconds = params.getFirstValue(Constants.lease_seconds).toInt
	  	log.info("Received hub challenge:\n-Mode: " + mode + "\n-Topic: " + topic + "\n-Lease: " + lease_seconds + " seconds")
		response.setEntity(params.getFirstValue(Constants.challenge),MediaType.TEXT_PLAIN)
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

/*
 * Main class to subscribe / unsubscribe to a list of topics.
 * The list is specified in an xml config file.
 * 
*/
class Subscriber(val hostname:String, val port : Int,val subscribe : Boolean) extends Logging{

  val httpClient = new Client(Protocol.HTTP);
  val pushRoute		= "push"
  val callbackURL   = "http://" + hostname + ":" + port + "/" + pushRoute 
  val restlet 		= new SubscriberRestlet
  val configFile    = "config.xml"
  
  val root : Component = new Component  
  root.getLogService.setEnabled(false)
  
  root.getServers.add(Protocol.HTTP, port)
  root.getDefaultHost.attach("/" + pushRoute + "/{ID}",restlet)
  root.start

  subscribeTopics(subscribe)
  
  def subscribeTopics(flag : Boolean ){
  	val config = XML.loadFile(configFile)
	(config\"feed").foreach( t => subUnsub( ( t \"@url").text,flag ))
  }

  /**
   * Retrieve and parse the Atom feed to subscribe to. 
   * Return an optional tuple containing one of the hub URLs and the topic URL.
   */
  def discover( atomURL : String) = {
    val response = httpClient.handle(new Request( Method.GET,atomURL))
    if (response.getStatus.isSuccess){
      val xml = XML.load (response.getEntity.getStream)
      val nodes = List("hub","self").map (v => (xml\"link").find ( link => link\"@rel" == v)) 
      (nodes) match {
        case List(Some(hubNode),Some(selfNode)) => Some((hubNode\"@href").toString,(selfNode\"@href").toString)
        case _ => None
      }
    }
    else None
  }
  
  def subscribe (atomURL : String) = subUnsub(atomURL,   true  )
  def unsubscribe (atomURL : String) = subUnsub(atomURL, false )
    
  def subUnsub (atomURL : String, flag : Boolean){
     discover(atomURL) match{
      case Some((hubURL,topicURL)) =>{
    	  log.info("Topic: " + topicURL + ", subscribe? " + flag)
    	  val form = new Form
		  val mode = if (flag) Constants.subscribe else Constants.unsubscribe
		  form.add(Constants.topic,topicURL)
		  form.add(Constants.mode, mode )
		  form.add(Constants.callback,callbackURL + "/" + topicURL.hashCode )
		  form.add(Constants.verify,Constants.sync)
       
   		  val response  = httpClient.handle(new Request(Method.POST,hubURL,form.getWebRepresentation))
		  if (!response.getStatus.isSuccess) log.error("Unable to " + mode + " to topic " + topicURL + ", HTTP status: " + response.getStatus.toString)
		}
      case None => log.error("Unable to fetch the hub or topic URL from the feed " + atomURL)
	}
  }
}

object Main {
	def main ( args: Array[String]) : Unit = {
	  if (args.length != 3){
		System.err.println("Please specify a hostname, a port number and true/false to subscribe/unsubscribe to/from the feeds.")  
	    exit(-1)
	  }else{
		System.setProperty("java.util.logging.config.file","log.properties")
		new Subscriber(args(0), args(1).toInt, args(2).toBoolean)
	  } 
	}
}



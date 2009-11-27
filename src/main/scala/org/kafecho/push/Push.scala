/*
 * Copyright 2009 Guillaume Belrose
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
*/
package org.kafecho.push

import scala.xml.XML
import scala.xml.Elem


import org.restlet.{Client,Component,Restlet};
import org.restlet.data._

import java.io.FileNotFoundException
import java.util.logging.LogManager

/**
 * A Scala singleton object with useful constants.
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

/**
 * Class representing a subscription to a PuSH enabled feed.
 */
class Subscription(val feedURL : String, val topicURL : String, val hubURL : String){
  val id : Int = feedURL.hashCode
  override def toString = "Feed URL: " + feedURL + ", topic URL: " + topicURL + ", Hub URL: " + hubURL +"." 
  def toXML = <feed id={id.toString} feedURL={feedURL} topicURL={topicURL} hubURL={hubURL} />
}

/*
 * Class which implements a PuSH subscriber.
 * For this to work, the host on which the subscriber is running must be recheable from the Internet.
*/
class Subscriber(val hostname:String, val port : Int) extends Logging{

  val httpClient = new Client(Protocol.HTTP);
  val pushRoute		= "push"
  val adminRoute 	= "admin"
  val callbackURL   = "http://" + hostname + ":" + port + "/" + pushRoute 
  val pushRestlet 		= new PuSHRestlet(this)
  val adminRestlet 		= new AdminRestlet(this)
  val configFile	    = "feeds.xml"

  var pendingSubscriptions : Set[Subscription] = Set()
  var activeSubscriptions : Set[Subscription] = Set()
  
  val root : Component = new Component  
  root.getLogService.setEnabled(false)
  
  root.getServers.add(Protocol.HTTP, port)
  
  root.getDefaultHost.attach("/" + pushRoute + "/{ID}",pushRestlet)
  root.getDefaultHost.attach("/" + adminRoute + "/{ID}",adminRestlet)
  root.getDefaultHost.attach("/" + adminRoute + "/",adminRestlet)

  root.start

  loadConfig
  
  def loadConfig{
    try{
    	val config = XML.loadFile(configFile)
    	(config\"feed").foreach{t => subUnsub( ( t \"@feedURL").text,true) }
    }catch{
      case ex: FileNotFoundException => 
    }
 }

  /**
   * Retrieve and parse the Atom feed to subscribe to. 
   * Return an optional tuple containing one of the hub URLs and the topic URL.
   */
  def discover( atomURL : String) = {
    val response = httpClient.handle(new Request( Method.GET,atomURL))
    if (response.getStatus.isSuccess){
      val xml = XML.load (response.getEntity.getStream)
      // Find link nodes containing a hub URL and a topic URL
      val nodes = List("self","hub").map (v => xml \"link" find ( _\"@rel" == v)) 
      (nodes) match {
        case List(Some(selfNode),Some(hubNode)) => Some( selfNode \ "@href" text, hubNode \ "@href" text)
        case _ => None
      }
    }
    else None
  }
  
  
  /**
   * Subscribe or unsubscribe to a PuSH enabled feed. 
   */
  def subUnsub(feedURL : String, topicURL : String, hubURL : String, flag : Boolean){
	  log.info("Topic: " + topicURL + ", subscribe? " + flag)

	  val s = new Subscription(feedURL, topicURL, hubURL)
	  pendingSubscriptions = pendingSubscriptions + s
   
	  val form = new Form
	  val mode = if (flag) Constants.subscribe else Constants.unsubscribe
	  form.add(Constants.topic,topicURL)
	  form.add(Constants.mode, mode )
	  form.add(Constants.callback,callbackURL + "/" + s.id )
	  form.add(Constants.verify,Constants.sync)
	  val response  = httpClient.handle(new Request(Method.POST,hubURL,form.getWebRepresentation))
	  if (!response.getStatus.isSuccess) log.error("Unable to " + mode + " to topic " + topicURL + ", HTTP status: " + response.getStatus.toString)
  }

  /**
   * Subscribe or unsubscribe to a feed. 
   * Will complain if the feed is not PuSH enabled.
   */
  def subUnsub (feedURL : String, flag : Boolean){
     discover(feedURL) match{
      case Some((topicURL,hubURL)) => subUnsub(feedURL,topicURL,hubURL,flag)
      case None => log.error("Unable to fetch the hub or topic URL from the feed " + feedURL)
	}
  }
  
  /**
   * Notification that a hub has accepted a request to subscribe to a feed.
   */
  def added (topicURL : String){
	  val found = pendingSubscriptions.find (_.topicURL == topicURL)
	  found match{
	    case Some(subscription) => activeSubscriptions = activeSubscriptions + subscription
	    case _ => log.error("Unable to find a subscription that matches " + topicURL)
	  }
	  saveSubscriptions
  }

  /**
   * Notification that a hub has accepted a request to unsubscribe from a feed.
   */
  def removed (topicURL : String){
	  activeSubscriptions = activeSubscriptions.filter( _.topicURL != topicURL)
	  saveSubscriptions
  }
  def saveSubscriptions : Unit = XML.saveFull(configFile, subscriptionsToXML , "UTF-8",true, null)
  
  def subscriptionsToXML= <feeds>{activeSubscriptions.map(_.toXML)}</feeds>
}	

/**
 * Main.
 */
object Main {
	def main ( args: Array[String]) : Unit = {
	  if (args.length != 2){
		System.err.println("Please specify a hostname and a port number.")  
	    exit(-1)
	  }else{
		LogManager.getLogManager.readConfiguration(getClass.getResourceAsStream("/log.properties"))
		new Subscriber(args(0), args(1).toInt)
		println ("Subscriber client is up.")
		println ("HTTP admin interface running @ http://" + args(0) +":" + args(1) + "/admin/")
	  } 
	}
}



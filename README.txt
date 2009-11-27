Last updated: 27/11/2009

Introduction:
-------------
scala-push is a simple subscriber client for the PubSubHubBub (PuSH) protocol built using the Scala language and the Restlet library.
It allows you to subscribe to PuSH enabled feeds and receive instant notifications as the content of those feeds is updated by publishers.
At present scala-push is purely a command line client and does not have a GUI.

Building and running scala-push:
--------------------------------
You need Maven installed on your machine and Java 6
From the top level directory, type: mvn assembly:directory
Go to target/scala-push-1.0-distro.dir/bin
Type ./launch.sh and specify your hostname and the port number you want to use. 
It is important that your hostname is reachable from the Internet otherwise it is not going to work.

Configuring scala-push:
-----------------------
scala-push runs a simple REST admin interface which does not yet have a GUI.

To add a subscription, do a POST:
curl -v -X POST -H 'Content-type: text/xml' -d '<feed feedURL=[your feed URL]"/>' http://[host]:[port]/admin/

To list the subscriptions, do a GET:
curl http://[host]:[port]/admin/

Example output:
<feeds>
		<feed feedURL="http://www.google.com/reader/public/atom/user%2F05268996354213702508%2Fstate%2Fcom.google%2Fbroadcast" topicURL="http://www.google.com/reader/public/atom/user%2F05268996354213702508%2Fstate%2Fcom.google%2Fbroadcast" hubURL="http://pubsubhubbub.appspot.com/" id="429658526">
		</feed>
</feeds>
Make a note of the field called id.

To delete a subscription, do a DELETE to url curl -x DELETE http://[host]:[port]/admin/[id] where id is the feed id.

As content is pushed to the subscriber, it will be displayed on the console.

Feeds to subscribe to:
----------------------
The following feeds are PuSH enabled and good candidates for testing:
-Google Alert feeds
-Shared Google Reader items


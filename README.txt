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
curl -d "feedURL=[your feed URL]" http://[host]:[port]/admin/
Example: curl -d "feedURL=http://www.google.com/reader/public/atom/user%2F05268996354213702508%2Fstate%2Fcom.google%2Fbroadcast" http://localhost:8080/admin/

To list the subscriptions, do a GET:
curl http://[host]:[port]/admin/
Example: curl http://localhost:8080/admin/

Example output:
<feeds>
	<feed>
		<id>-702581410</id>
		<feedURL>http://www.google.com/reader/public/atom/user/05268996354213702508/state/com.google/broadcast</feedURL>
		<topicURL>http://www.google.com/reader/public/atom/user/05268996354213702508/state/com.google/broadcast</topicURL>
		<hubURL>http://pubsubhubbub.appspot.com/</hubURL>
	</feed>
</feeds>

Make a note of the field called id.

To delete a subscription, do a DELETE: curl -X DELETE http://[host]:[port]/admin/[id] where id is the feed id.
Example: curl -X DELETE http://localhost:8080/admin/-702581410

As content is pushed to the subscriber, it will be displayed on the console.
Here is an example below: 

Fri Nov 27 15:51:43 GMT 2009 --- Update from: Guillaume's shared items in Google Reader
Topic URL: http://www.google.com/reader/public/atom/user%2F05268996354213702508%2Fstate%2Fcom.google%2Fbroadcast

10 NoSQL Systems Reviewed (published Mon Nov 09 16:04:02 GMT 2009)
http://feedproxy.google.com/~r/HighScalability/~3/8arewmYgZ7k/10-nosql-systems-reviewed.html

Feeds to subscribe to:
----------------------
The following feeds are PuSH enabled and good candidates for testing:
-Google Alert feeds
-Shared Google Reader items: the subscriber receives a notification as soon as you share an item.
